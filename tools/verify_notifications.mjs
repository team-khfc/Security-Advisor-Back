// 합성 계정·신청만 만든다. 기한 재실행도 합성 신청 ID 하나로 제한한다.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {writeFile} from 'node:fs/promises';
const origin=process.env.VERIFY_ORIGIN??'http://127.0.0.1:3001';
const password=process.env.SYNTHETIC_ACCOUNT_PASSWORD;
if(!password||!process.env.DEMO_ACCOUNT_PASSWORD)throw new Error('검증 계정 비밀번호 환경변수가 필요합니다.');
const report={prefix:`notice-${randomUUID().slice(0,8)}`,accounts:{},resources:{},checks:[]};
function ok(name,value){assert.ok(value,name);report.checks.push(name);}
async function client(username,pass=password){
 const jar=new Map();
 async function raw(method,path,body){
  const headers={'X-Requested-With':'SecurityAdvisor',Cookie:[...jar].map(([k,v])=>`${k}=${v}`).join('; ')};
  if(jar.has('XSRF-TOKEN'))headers['X-XSRF-TOKEN']=decodeURIComponent(jar.get('XSRF-TOKEN'));
  if(body!==undefined)headers['Content-Type']='application/json';
  const r=await fetch(origin+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});
  for(const c of r.headers.getSetCookie()){const a=c.split(';')[0],i=a.indexOf('=');jar.set(a.slice(0,i),a.slice(i+1));}
  const text=await r.text();return {status:r.status,data:text?JSON.parse(text):null};
 }
 async function call(method,path,body){const r=await raw(method,path,body);assert.ok(r.status>=200&&r.status<300,`${method} ${path}: ${r.status} ${r.data?.detail??''}`);return r.data;}
 await raw('GET','/api/auth/me');if(username)await call('POST','/api/auth/login',{username,password:pass});return {call,raw};
}
const admin=await client(process.env.VERIFY_ADMIN_USERNAME??'admin',process.env.DEMO_ACCOUNT_PASSWORD), signup=await client();
const actors={},pools={};
for(const [name,role] of Object.entries({owner:'APPLICANT',outsider:'APPLICANT',oldinfra:'INFRA',newinfra:'INFRA',oldsecurity:'SECURITY',newsecurity:'SECURITY',uiadmin:'ADMIN'})){
 const username=`${report.prefix}-${name}`;report.accounts[name]=username;
 await signup.call('POST','/api/members',{username,password,displayName:`합성 알림 ${name}`,department:'합성 알림 검증부'});
 if(role!=='APPLICANT')await admin.call('PUT',`/api/members/${username}/role`,{role,reason:'합성 알림 검증'});
 if(['INFRA','SECURITY'].includes(role))pools[name]=await admin.call('POST','/api/reviewer-pools',{field:role,memberUsername:username,defaultReviewer:false});
 actors[name]=await client(username);
}
const get=(actor,path)=>actor.call('GET',path);
async function create(suffix){
 const c=await actors.owner.call('POST','/api/business-cases',{title:`${report.prefix} ${suffix}`,summary:'실제 장비와 무관한 합성 알림 검증 사업',department:'합성 알림 검증부',docTypes:['FIREWALL']});
 const id=c.requests[0].securityRequestId;await actors.owner.call('POST',`/api/security-requests/${id}/reviews`,{expectedRevision:0});return {caseId:c.businessCaseId,id};
}
async function assign(id,field,name){return admin.call('POST',`/api/security-requests/${id}/assignments`,{field,assigneeUsername:report.accounts[name],reason:'합성 알림·업무 인계 검증'});}
async function all(actor,unreadOnly=false){
 const out=[];for(let page=0;;page++){const r=await get(actor,`/api/notifications?page=${page}&size=100&unreadOnly=${unreadOnly}`);out.push(...r.content);if(out.length>=r.totalElements||!r.content.length)return out;}
}
try{
 const c=await create('자동 검증');const id=c.id;Object.assign(report.resources,{apiCase:c.caseId,apiRequest:id});
 await assign(id,'INFRA','oldinfra');await assign(id,'SECURITY','oldsecurity');
 ok('새 담당자 배정 알림',(await all(actors.oldinfra)).some(n=>n.kind==='REASSIGNED'&&n.link===`/requests/${id}`));
 ok('다른 신청자 알림·할 일 비공개',(await all(actors.outsider)).length===0&&(await get(actors.outsider,'/api/todos')).totalElements===0);
 for(let i=0;i<105;i++)await assign(id,'INFRA','oldinfra');
 const notices=await all(actors.oldinfra);ok('알림 100건 초과 누락 없는 페이지 조회',notices.length===106&&new Set(notices.map(n=>n.notificationId)).size===106);
 ok('알림 최신순 안정 정렬',notices.every((n,i)=>i===0||notices[i-1].notificationId>n.notificationId));
 const first=notices[0],before=await get(actors.oldinfra,'/api/todo-summary');
 const a=await actors.oldinfra.call('PATCH',`/api/notifications/${first.notificationId}`,{read:true});
 const b=await actors.oldinfra.call('PATCH',`/api/notifications/${first.notificationId}`,{read:true});
 ok('읽음 멱등·안 읽음 건수',a.readAt===b.readAt&&(await get(actors.oldinfra,'/api/notifications/unread-count')).unread===105);
 ok('읽음 처리와 업무 건수 분리',(await get(actors.oldinfra,'/api/todo-summary')).total===before.total);
 ok('다른 사람 알림 읽음 차단',(await actors.newinfra.raw('PATCH',`/api/notifications/${first.notificationId}`,{read:true})).status===404);
 const ds=await get(admin,`/api/security-requests/${id}/deadlines`),due=ds.find(d=>d.field==='INFRA'&&d.active).dueDate;
 const bc=await get(actors.owner,`/api/business-cases/${c.caseId}`),pub=await get(admin,`/api/publications/${bc.publicationId}`);
 const holidays=new Set(pub.fieldSchema.deadlinePolicy.holidays);let prior=new Date(due+'T12:00:00Z');
 do{prior.setUTCDate(prior.getUTCDate()-1);}while([0,6].includes(prior.getUTCDay())||holidays.has(prior.toISOString().slice(0,10)));
 const run=asOf=>admin.call('POST','/api/deadline-reminder-runs',{asOf,securityRequestId:id});
 await run(prior.toISOString().slice(0,10));await run(prior.toISOString().slice(0,10));
 ok('1영업일 전 기한 알림과 중복 방지',(await all(actors.oldinfra)).filter(n=>n.kind==='DEADLINE_IMMINENT').length===1);
 const rev=(await get(actors.oldinfra,`/api/security-requests/${id}/reviews`)).find(r=>r.field==='INFRA');
 const supplement=await actors.oldinfra.call('POST',`/api/reviews/${rev.reviewId}/supplements`,{fieldKeys:['project.period'],reason:'합성 인계 후 답변 전달 확인',expectedRevision:rev.revision});
 ok('신청자 보완 알림과 처리할 일',(await all(actors.owner)).some(n=>n.kind==='SUPPLEMENT_REQUESTED')&&(await get(actors.owner,'/api/todo-summary')).openSupplements===1);
 await admin.call('PUT',`/api/members/${report.accounts.oldinfra}/activation`,{active:false,reason:'합성 비활성화 인계'});
 ok('비활성 계정 기존 세션 차단',(await actors.oldinfra.raw('GET','/api/notifications')).status===401);
 await admin.call('PUT',`/api/members/${report.accounts.oldsecurity}/role`,{role:'APPLICANT',reason:'합성 역할 변경 인계'});
 ok('역할 변경 기존 세션 차단',(await actors.oldsecurity.raw('GET','/api/todo-summary')).status===401);
 actors.oldsecurity=await client(report.accounts.oldsecurity);
 ok('역할 변경 후 알림·할 일 차단',(await all(actors.oldsecurity)).length===0&&(await get(actors.oldsecurity,'/api/todos')).totalElements===0);
 ok('관리자 재배정 필요 알림',(await all(actors.uiadmin)).some(n=>n.kind==='REASSIGNMENT_REQUIRED'&&n.link===`/requests/${id}`));
 const after=new Date(due+'T12:00:00Z');after.setUTCDate(after.getUTCDate()+1);const overdue=after.toISOString().slice(0,10);await run(overdue);
 await admin.call('PUT',`/api/members/${report.accounts.oldinfra}/activation`,{active:true,reason:'합성 검증 복원'});actors.oldinfra=await client(report.accounts.oldinfra);
 ok('비활성 중 기한 알림 수신 제외',!(await all(actors.oldinfra)).some(n=>n.kind==='DEADLINE_OVERDUE'));
 await admin.call('PUT',`/api/reviewer-pools/${pools.oldinfra.reviewerPoolId}/activation`,{active:false,reason:'합성 분야 등록 비활성'});await run(overdue);
 ok('분야 등록 비활성 시 알림·할 일 제외',(await all(actors.oldinfra)).length===0&&(await get(actors.oldinfra,'/api/todos')).totalElements===0);
 await admin.call('PUT',`/api/reviewer-pools/${pools.oldinfra.reviewerPoolId}/activation`,{active:true,reason:'합성 분야 복원'});
 await assign(id,'INFRA','newinfra');await assign(id,'SECURITY','newsecurity');
 ok('재배정 직후 이전 담당자 목록·건수·읽음 차단',(await all(actors.oldinfra)).length===0&&(await get(actors.oldinfra,'/api/notifications/unread-count')).unread===0&&(await actors.oldinfra.raw('PATCH',`/api/notifications/${first.notificationId}`,{read:true})).status===404);
 const answered=await actors.owner.call('POST',`/api/supplements/${supplement.supplementId}/answers`,{note:'합성 사업 보완 설명',expectedRevision:supplement.revision});
 ok('인계 뒤 보완 답변은 현재 담당자에게 전달',(await all(actors.newinfra)).some(n=>n.kind==='SUPPLEMENT_ANSWERED')&&(await all(actors.oldinfra)).length===0);
 ok('새 담당자의 답변 검토 할 일',(await get(actors.newinfra,'/api/todo-summary')).answeredSupplements===1);
 await actors.newinfra.call('PUT',`/api/supplements/${answered.supplementId}/resolution`,{note:'합성 답변 검토 완료',expectedRevision:answered.revision});
 ok('보완 해소 후 처리할 일 감소',(await get(actors.newinfra,'/api/todo-summary')).answeredSupplements===0);
 await run(overdue);await run(overdue);
 ok('새 담당자 경과 알림·재실행 중복 방지',(await all(actors.newinfra)).filter(n=>n.kind==='DEADLINE_OVERDUE').length===1);
 const request=await get(actors.owner,`/api/security-requests/${id}`);
 await actors.owner.call('PUT',`/api/security-requests/${id}/withdrawal`,{expectedRevision:request.revision,reason:'합성 알림 검증 종료'});
 ok('종료 업무는 처리할 일에서 제외',(await get(actors.newinfra,'/api/todos')).totalElements===0);
 const n=(await all(actors.newinfra)).length;await run(overdue);ok('종료 업무 기한 알림 중단',(await all(actors.newinfra)).length===n);
 await admin.call('PUT',`/api/members/${report.accounts.oldsecurity}/role`,{role:'SECURITY',reason:'합성 화면 검증 준비'});
 const ui=await create('실제 화면 검증');Object.assign(report.resources,{uiCase:ui.caseId,uiRequest:ui.id,pools});
 await assign(ui.id,'INFRA','oldinfra');await assign(ui.id,'SECURITY','oldsecurity');
 console.log(`합성 API 검증 통과: ${report.checks.length}개, 화면 검증 신청 #${ui.id}`);
}finally{
 if(process.env.VERIFY_OUTPUT)await writeFile(process.env.VERIFY_OUTPUT,JSON.stringify(report,null,2));
}
