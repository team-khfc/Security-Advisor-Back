// 합성 자원만 생성한다. 실행 전 VERIFY_ORIGIN·DEMO_ACCOUNT_PASSWORD를 환경변수로 지정한다.
// 실제 장비·외부 AI·기본 담당자 설정을 변경하지 않는다. 생성한 계정·신청·이력은 검증 후 보존한다.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { writeFile } from "node:fs/promises";
const origin = process.env.VERIFY_ORIGIN ?? "http://127.0.0.1:3001";
const password = process.env.DEMO_ACCOUNT_PASSWORD;
if (!password) throw new Error("DEMO_ACCOUNT_PASSWORD 환경변수가 필요합니다.");
const prefix = `p0-${randomUUID().slice(0, 8)}`;
const report = { prefix, checks: [], accounts: {}, resources: {} };
function ok(name, condition) {
  assert.ok(condition, name);
  report.checks.push(name);
}
async function client(username) {
  const jar = new Map();
  async function raw(method, path, body, extra = {}) {
    const headers = {
      "X-Requested-With": "SecurityAdvisor",
      Cookie: [...jar].map(([k, v]) => `${k}=${v}`).join("; "),
      ...extra,
    };
    if (jar.has("XSRF-TOKEN"))
      headers["X-XSRF-TOKEN"] = decodeURIComponent(jar.get("XSRF-TOKEN"));
    const multipart = body instanceof FormData;
    if (body !== undefined && !multipart)
      headers["Content-Type"] = "application/json";
    const response = await fetch(origin + path, {
      method,
      headers,
      body:
        body === undefined
          ? undefined
          : multipart
            ? body
            : JSON.stringify(body),
    });
    for (const cookie of response.headers.getSetCookie()) {
      const first = cookie.split(";")[0],
        i = first.indexOf("=");
      jar.set(first.slice(0, i), first.slice(i + 1));
    }
    const bytes = Buffer.from(await response.arrayBuffer());
    const data =
      response.headers.get("content-type")?.includes("json") && bytes.length
        ? JSON.parse(bytes)
        : bytes;
    return { status: response.status, data };
  }
  async function call(method, path, body, extra) {
    const r = await raw(method, path, body, extra);
    assert.ok(
      r.status >= 200 && r.status < 300,
      `${method} ${path}: ${r.status} ${r.data?.detail ?? ""}`,
    );
    return r.data;
  }
  await raw("GET", "/api/auth/me");
  if (username) await call("POST", "/api/auth/login", { username, password });
  return { raw, call };
}
async function status(c, method, path, expected, body) {
  const r = await c.raw(method, path, body);
  assert.equal(r.status, expected, `${method} ${path}`);
  report.checks.push(`${method} ${path}: ${expected}`);
  return r.data;
}
console.log("합성 검증 시작: " + prefix);
const admin = await client(process.env.VERIFY_ADMIN_USERNAME ?? "admin");
const registration = await client();
for (const [name, role] of Object.entries({
  owner: "APPLICANT",
  other: "APPLICANT",
  infra: "INFRA",
  security: "SECURITY",
  outsider: "INFRA",
  replacement: "INFRA",
  adminTarget: "ADMIN",
})) {
  const username = `${prefix}-${name.toLowerCase()}`;
  report.accounts[name] = username;
  await registration.call("POST", "/api/members", {
    username,
    password,
    displayName: `합성 권한검증 ${name}`,
    department: "합성 권한검증부",
  });
  if (role !== "APPLICANT")
    await admin.call("PUT", `/api/members/${username}/role`, {
      role,
      reason: "합성 P0 검증 계정 역할 지정",
    });
  if (["infra", "security", "replacement"].includes(name))
    await admin.call("POST", "/api/reviewer-pools", {
      field: role,
      memberUsername: username,
      defaultReviewer: false,
    });
}
const actors = {};
for (const [name, user] of Object.entries(report.accounts))
  actors[name] = await client(user);
const { owner, infra, security, other, outsider, replacement } = actors;
for (const actor of [infra, security, admin])
  await status(actor, "POST", "/api/business-cases", 403, {
    title: "생성되면 안 되는 합성 사업",
    docTypes: ["FIREWALL"],
  });
const business = await owner.call("POST", "/api/business-cases", {
  title: `합성 P0 권한·세션 검증 ${prefix}`,
  summary: "합성 데이터로 권한 경계와 기존 세션 차단을 검증합니다.",
  department: "합성 검증부",
  docTypes: ["FIREWALL", "SECURITY_REVIEW"],
});
const caseId = business.businessCaseId,
  rid = business.requests.find(
    (r) => r.docType === "FIREWALL",
  ).securityRequestId,
  sibling = business.requests.find(
    (r) => r.docType === "SECURITY_REVIEW",
  ).securityRequestId;
report.resources = { caseId, requestId: rid, sibling };
const file = new FormData();
file.set(
  "file",
  new Blob(["합성 권한검증 자료. 실제 기관 정보 없음."], {
    type: "text/plain",
  }),
  "synthetic-access.txt",
);
await owner.call("POST", `/api/business-cases/${caseId}/uploaded-files`, file);
const doc = await owner.call(
  "POST",
  `/api/security-requests/${rid}/generated-documents`,
  { includeAiSupplement: false },
);
const siblingDoc = await owner.call(
  "POST",
  `/api/security-requests/${sibling}/generated-documents`,
  { includeAiSupplement: false },
);
const did = doc.generatedDocumentId;
for (const actor of [infra, security, outsider, other]) {
  for (const path of [
    `/api/business-cases/${caseId}`,
    `/api/business-cases/${caseId}/uploaded-files`,
    `/api/security-requests/${rid}`,
    `/api/security-requests/${rid}/documents`,
    `/api/generated-documents/${did}/download?format=PDF`,
  ])
    await status(actor, "GET", path, 404);
  const list = await actor.call(
    "GET",
    `/api/security-requests?businessCaseId=${caseId}`,
  );
  ok("미배정·타인 목록 0", list.totalElements === 0);
}
for (const actor of [owner, infra, security, outsider])
  await status(actor, "GET", "/api/insights/summary", 403);
await status(admin, "GET", "/api/insights/summary", 200);
for (const [key, value] of Object.entries({
  "project.period": "2026-12-01~2027-11-30",
  "data.personal_info": "false",
  "system.external_transfer": "true",
}))
  await owner.call("PUT", `/api/business-cases/${caseId}/answers/${key}`, {
    value,
  });
for (const [key, value] of Object.entries({
  "firewall.protocol": "TCP",
  "firewall.port": "443",
  "firewall.period": "2026-12-01~2027-11-30",
  "firewall.purpose": "합성 HTTPS 조회",
}))
  await owner.call("PUT", `/api/security-requests/${rid}/answers/${key}`, {
    value,
  });
await owner.call("POST", `/api/security-requests/${rid}/reviews`, {
  expectedRevision: (await owner.call("GET", `/api/security-requests/${rid}`))
    .revision,
});
const reviews = await owner.call(
  "GET",
  "/api/security-requests/" + rid + "/reviews",
);
for (const field of ["INFRA", "SECURITY"])
  await admin.call("POST", `/api/security-requests/${rid}/assignments`, {
    field,
    assigneeUsername: report.accounts[field.toLowerCase()],
    reason: "합성 P0 검증용 재배정",
  });
for (const actor of [infra, security]) {
  const c = await actor.call("GET", `/api/business-cases/${caseId}`);
  ok(
    "배정 사업의 미배정 형제 신청 숨김",
    c.requests.length === 1 && c.requests[0].securityRequestId === rid,
  );
  const cases = await actor.call("GET", "/api/business-cases?size=100");
  ok(
    "배정된 사업 목록만 조회",
    cases.totalElements === 1 && cases.content[0].businessCaseId === caseId,
  );
  for (const path of [
    `/api/security-requests/${rid}`,
    `/api/business-cases/${caseId}/uploaded-files`,
    `/api/generated-documents/${did}/download?format=PDF`,
  ])
    await status(actor, "GET", path, 200);
  await status(actor, "GET", `/api/security-requests/${sibling}`, 404);
  await status(
    actor,
    "GET",
    `/api/generated-documents/${siblingDoc.generatedDocumentId}/download?format=PDF`,
    404,
  );
  const hidden = await actor.call(
    "GET",
    `/api/audit-events?targetType=SECURITY_REQUEST&targetId=${sibling}`,
  );
  ok("미배정 형제 이력 0", hidden.totalElements === 0);
}
const ir = reviews.find((r) => r.field === "INFRA").reviewId,
  sr = reviews.find((r) => r.field === "SECURITY").reviewId;
for (const [key, value] of Object.entries({
  "system.target_server": "합성 검증 서버",
  "network.source": "192.0.2.10",
  "network.destination": "198.51.100.20",
}))
  await infra.call("PUT", `/api/reviews/${ir}/answers/${key}`, { value });
await infra.call("PUT", `/api/reviews/${ir}/allocation-completion`, {
  expectedRevision: (await infra.call("GET", `/api/reviews/${ir}`)).revision,
});
for (const [actor, id, keys] of [
  [infra, ir, ["source", "destination", "protocol", "port", "period"]],
  [
    security,
    sr,
    ["processed_info", "external_transfer", "access_control", "logging"],
  ],
]) {
  await actor.call("PUT", `/api/reviews/${id}/completion`, {
    expectedRevision: (await actor.call("GET", `/api/reviews/${id}`)).revision,
    opinion: "합성 권한 검증 완료",
    checklist: Object.fromEntries(keys.map((k) => [k, true])),
  });
}
const draft = await owner.call(
  "POST",
  `/api/security-requests/${rid}/final-drafts`,
  {
    text: "합성 P0 검증 최종 보고서. 실제 장비를 변경하지 않습니다.",
    expectedRevision: (await owner.call("GET", `/api/security-requests/${rid}`))
      .revision,
  },
);
await owner.call(
  "PUT",
  `/api/final-drafts/${draft.finalDraftId}/confirmation`,
  { text: draft.text, expectedRevision: draft.revision },
);
const submission = await owner.call(
  "POST",
  `/api/security-requests/${rid}/submissions`,
  {
    expectedRevision: (await owner.call("GET", `/api/security-requests/${rid}`))
      .revision,
  },
  { "Idempotency-Key": `${prefix}-submission` },
);
const sid = submission.submissionId;
report.resources.submissionId = sid;
for (const actor of [infra, security])
  await actor.call("POST", `/api/submissions/${sid}/final-confirmations`, {
    opinion: "합성 확인",
    expectedRevision: (await actor.call("GET", `/api/security-requests/${rid}`))
      .revision,
  });
await security.call("PUT", `/api/submissions/${sid}/confirmation`, {
  expectedRevision: (
    await security.call("GET", `/api/security-requests/${rid}`)
  ).revision,
});
const finalDoc = await owner.call(
  "POST",
  `/api/security-requests/${rid}/generated-documents`,
  { submissionId: sid, includeAiSupplement: false },
);
const rule = (
  await infra.call("GET", `/api/security-requests/${rid}/firewall-rules`)
)[0];
report.resources.ruleId = rule.firewallRuleId;
report.resources.documentId = finalDoc.generatedDocumentId;
for (const actor of [owner, infra, security, admin]) {
  await status(actor, "GET", `/api/submissions/${sid}`, 200);
  const pdf = await actor.call(
    "GET",
    `/api/generated-documents/${finalDoc.generatedDocumentId}/download?format=PDF`,
  );
  ok("권한 있는 사용자의 PDF 정상", pdf.subarray(0, 5).toString() === "%PDF-");
  const box = await actor.call("GET", "/api/submissions");
  ok(
    "문서함의 허용 제출본",
    box.some((v) => v.submissionId === sid),
  );
}
for (const actor of [outsider, other]) {
  await status(actor, "GET", `/api/submissions/${sid}`, 404);
  await status(
    actor,
    "GET",
    `/api/generated-documents/${finalDoc.generatedDocumentId}/download?format=PDF`,
    404,
  );
  ok(
    "문서함의 미허용 제출본 제외",
    !(await actor.call("GET", "/api/submissions")).some(
      (v) => v.submissionId === sid,
    ),
  );
  ok(
    "미허용 규칙 제외",
    !(await actor.call("GET", "/api/firewall-rules")).some(
      (v) => v.firewallRuleId === rule.firewallRuleId,
    ),
  );
}
await status(
  outsider,
  "PUT",
  `/api/firewall-rules/${rule.firewallRuleId}/application`,
  404,
  {
    result: "APPLIED",
    expectedRevision: rule.revision,
    note: "실행되면 안 되는 합성 기록",
  },
);
ok(
  "거부된 규칙 기록은 상태 불변",
  (await infra.call("GET", `/api/security-requests/${rid}/firewall-rules`))[0]
    .status === rule.status,
);
// 진행 중인 형제 신청으로 재배정에 따른 이전 담당자의 접근 회수를 검증한다.
await owner.call("POST", `/api/security-requests/${sibling}/reviews`, {
  expectedRevision: 0,
});
for (const user of [report.accounts.infra, report.accounts.replacement])
  await admin.call("POST", `/api/security-requests/${sibling}/assignments`, {
    field: "INFRA",
    assigneeUsername: user,
    reason: "합성 이전 담당자 접근 회수 검증",
  });
await status(infra, "GET", `/api/security-requests/${sibling}`, 404);
await status(replacement, "GET", `/api/security-requests/${sibling}`, 200);
ok(
  "이전 담당자의 이력 접근 회수",
  (
    await infra.call(
      "GET",
      `/api/audit-events?targetType=SECURITY_REQUEST&targetId=${sibling}`,
    )
  ).totalElements === 0,
);
async function change(name, resource, value) {
  const user = report.accounts[name];
  assert.ok(user?.startsWith(prefix + "-"));
  return admin.call("PUT", `/api/members/${user}/${resource}`, {
    ...value,
    reason: "합성 P0 세션 차단 검증",
  });
}
const secondInfra = await client(report.accounts.infra);
await change("infra", "activation", { active: false });
await status(infra, "GET", `/api/security-requests/${rid}`, 401);
await status(secondInfra, "POST", "/api/sessions/current/renewals", 401, {});
const anonymous = await client();
await status(anonymous, "POST", "/api/auth/login", 401, {
  username: report.accounts.infra,
  password,
});
await change("infra", "activation", { active: true });
const freshInfra = await client(report.accounts.infra);
await status(freshInfra, "GET", `/api/security-requests/${rid}`, 200);
const dormant = await client(report.accounts.infra);
await change("infra", "activation", { active: false });
await change("infra", "activation", { active: true });
await status(dormant, "GET", "/api/auth/me", 401);
await change("replacement", "role", { role: "SECURITY" });
await status(replacement, "POST", "/api/sessions/current/renewals", 401, {});
const changed = await client(report.accounts.replacement);
await status(changed, "GET", `/api/security-requests/${sibling}`, 404);
ok(
  "현재 역할과 다른 옛 배정의 목록 제외",
  (await changed.call("GET", `/api/security-requests?businessCaseId=${caseId}`))
    .totalElements === 0,
);
const roundTrip = await client(report.accounts.replacement);
await change("replacement", "role", { role: "INFRA" });
await change("replacement", "role", { role: "SECURITY" });
await status(roundTrip, "GET", "/api/auth/me", 401);
const oldAdmin = actors.adminTarget;
await status(oldAdmin, "GET", "/api/members", 200);
await change("adminTarget", "role", { role: "APPLICANT" });
await status(oldAdmin, "GET", "/api/members", 401);
const freshApplicant = await client(report.accounts.adminTarget);
await status(freshApplicant, "GET", "/api/members", 403);
await change("adminTarget", "role", { role: "APPLICANT" });
await change("adminTarget", "activation", { active: true });
await status(freshApplicant, "GET", "/api/auth/me", 200);
const oldOwner = await client(report.accounts.owner);
await change("owner", "activation", { active: false });
await status(oldOwner, "POST", "/api/business-cases", 401, {
  title: "차단되어야 할 합성 신청",
  docTypes: ["FIREWALL"],
});
await change("owner", "activation", { active: true });
report.passed = report.checks.length;
report.finishedAt = new Date().toISOString();
if (process.env.VERIFY_REPORT)
  await writeFile(process.env.VERIFY_REPORT, JSON.stringify(report, null, 2));
console.log(
  JSON.stringify({
    passed: report.passed,
    prefix,
    resources: report.resources,
  }),
);
