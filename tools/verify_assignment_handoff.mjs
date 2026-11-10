// 합성 계정·신청만 생성한다. 실제 장비·외부 AI는 호출하지 않는다.
// 기본 담당자를 시험하는 동안 기존 지정 ID를 보관하고 finally에서 복구한다.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { writeFile } from "node:fs/promises";
const origin = process.env.VERIFY_ORIGIN ?? "http://127.0.0.1:3001";
const password =
  process.env.SYNTHETIC_ACCOUNT_PASSWORD ?? process.env.DEMO_ACCOUNT_PASSWORD;
if (!password || !process.env.DEMO_ACCOUNT_PASSWORD)
  throw new Error("검증 계정 비밀번호 환경변수가 필요합니다.");
const report = {
  prefix: `handoff-${randomUUID().slice(0, 8)}`,
  accounts: {},
  resources: {},
  checks: [],
};
function ok(name, value) {
  assert.ok(value, name);
  report.checks.push(name);
}
async function client(username, pass = password) {
  const jar = new Map();
  async function raw(method, path, body) {
    const headers = {
      "X-Requested-With": "SecurityAdvisor",
      Cookie: [...jar].map(([k, v]) => `${k}=${v}`).join("; "),
    };
    if (jar.has("XSRF-TOKEN"))
      headers["X-XSRF-TOKEN"] = decodeURIComponent(jar.get("XSRF-TOKEN"));
    if (body !== undefined) headers["Content-Type"] = "application/json";
    const r = await fetch(origin + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    for (const cookie of r.headers.getSetCookie()) {
      const first = cookie.split(";")[0],
        i = first.indexOf("=");
      jar.set(first.slice(0, i), first.slice(i + 1));
    }
    const text = await r.text();
    return { status: r.status, data: text ? JSON.parse(text) : null };
  }
  async function call(method, path, body) {
    const r = await raw(method, path, body);
    assert.ok(
      r.status >= 200 && r.status < 300,
      `${method} ${path}: ${r.status} ${r.data?.detail ?? ""}`,
    );
    return r.data;
  }
  await raw("GET", "/api/auth/me");
  if (username)
    await call("POST", "/api/auth/login", { username, password: pass });
  return { raw, call };
}
const admin = await client(
    process.env.VERIFY_ADMIN_USERNAME ?? "admin",
    process.env.DEMO_ACCOUNT_PASSWORD,
  ),
  signup = await client();
const roles = {
  owner: "APPLICANT",
  oldinfra: "INFRA",
  newinfra: "INFRA",
  oldsecurity: "SECURITY",
  newsecurity: "SECURITY",
  uiinfra: "INFRA",
  uiadmin: "ADMIN",
};
const pools = {};
for (const [name, role] of Object.entries(roles)) {
  const username = `${report.prefix}-${name}`;
  report.accounts[name] = username;
  await signup.call("POST", "/api/members", {
    username,
    password,
    displayName: `합성 인계 ${name}`,
    department: "합성 업무인계 검증부",
  });
  if (role !== "APPLICANT")
    await admin.call("PUT", `/api/members/${username}/role`, {
      role,
      reason: "합성 업무 인계 검증",
    });
  if (["oldinfra", "newinfra", "oldsecurity", "newsecurity"].includes(name))
    pools[name] = await admin.call("POST", "/api/reviewer-pools", {
      field: role,
      memberUsername: username,
      defaultReviewer: false,
    });
}
const actors = {};
for (const [name, username] of Object.entries(report.accounts))
  actors[name] = await client(username);
const defaults = (await admin.call("GET", "/api/reviewer-pools")).filter(
  (p) => p.defaultReviewer && p.active,
);
report.defaults = defaults.map((p) => ({
  id: p.reviewerPoolId,
  field: p.field,
  username: p.memberUsername,
}));
report.pools = pools;
const { owner, oldinfra, newinfra, oldsecurity } = actors;
async function create(title, docTypes = ["FIREWALL"]) {
  return owner.call("POST", "/api/business-cases", {
    title: `합성 ${title} ${report.prefix}`,
    department: "합성 인계 검증부",
    docTypes,
  });
}
async function start(id) {
  return owner.call("POST", `/api/security-requests/${id}/reviews`, {
    expectedRevision: 0,
  });
}
async function assign(id, field, username) {
  return admin.call("POST", `/api/security-requests/${id}/assignments`, {
    field,
    assigneeUsername: username,
    reason: "합성 업무 인계: 기존 답변과 협의 보존",
  });
}
async function issue(id, field, reason) {
  const assignments = await admin.call(
    "GET",
    `/api/security-requests/${id}/assignments`,
  );
  ok(
    `${id} 상세 ${field} ${reason ?? "정상"}`,
    assignments.find((a) => a.active && a.field === field)
      ?.reassignmentReason === reason,
  );
  const listing = await admin.call("GET", `/api/security-requests?query=${id}`);
  ok(
    `${id} 목록·상세 사유 일치`,
    listing.content
      .find((r) => r.securityRequestId === id)
      .overview.fields.find((f) => f.field === field).reassignmentReason ===
      reason,
  );
}
try {
  const c = await create("API 업무 인계", ["FIREWALL", "SECURITY_REVIEW"]);
  const id = c.requests.find((r) => r.docType === "FIREWALL").securityRequestId;
  const sibling = c.requests.find(
    (r) => r.docType === "SECURITY_REVIEW",
  ).securityRequestId;
  report.resources.apiCase = c.businessCaseId;
  report.resources.apiRequest = id;
  report.resources.sibling = sibling;
  await start(id);
  await assign(id, "INFRA", report.accounts.oldinfra);
  await assign(id, "SECURITY", report.accounts.oldsecurity);
  const reviews = await admin.call(
      "GET",
      `/api/security-requests/${id}/reviews`,
    ),
    ir = reviews.find((r) => r.field === "INFRA").reviewId;
  await oldinfra.call(
    "PUT",
    `/api/reviews/${ir}/answers/system.target_server`,
    { value: "합성 인계 보존 서버" },
  );
  const before = await admin.call(
    "GET",
    `/api/security-requests/${id}/reviews`,
  );
  ok(
    "미배정 신규 담당자 상세 차단",
    (await newinfra.raw("GET", `/api/security-requests/${id}`)).status === 404,
  );
  await admin.call("PUT", `/api/members/${report.accounts.oldinfra}/role`, {
    role: "SECURITY",
    reason: "합성 전보",
  });
  ok(
    "역할 변경 기존 세션 401",
    (await oldinfra.raw("GET", "/api/auth/me")).status === 401,
  );
  await issue(id, "INFRA", "ROLE_CHANGED");
  ok(
    "부적격 기본 지정 422",
    (
      await admin.raw(
        "PUT",
        `/api/reviewer-pools/${pools.oldinfra.reviewerPoolId}/default`,
      )
    ).status === 422,
  );
  await admin.call(
    "PUT",
    `/api/reviewer-pools/${pools.newinfra.reviewerPoolId}/default`,
  );
  await start(sibling);
  const siblingAssignments = await admin.call(
    "GET",
    `/api/security-requests/${sibling}/assignments`,
  );
  ok(
    "부적격 사업 담당자 대신 유효한 기본 담당자 자동 배정",
    siblingAssignments.some(
      (a) =>
        a.active &&
        a.field === "INFRA" &&
        a.assigneeUsername === report.accounts.newinfra,
    ),
  );
  await assign(id, "INFRA", report.accounts.newinfra);
  await issue(id, "INFRA", null);
  ok(
    "새 담당자 기존 공통 답변 인계",
    JSON.stringify(
      await newinfra.call("GET", `/api/business-cases/${c.businessCaseId}`),
    ).includes("합성 인계 보존 서버"),
  );
  ok(
    "협의 기록 동일",
    JSON.stringify(
      await admin.call("GET", `/api/security-requests/${id}/reviews`),
    ) === JSON.stringify(before),
  );
  const former = await client(report.accounts.oldinfra);
  ok(
    "전임자 재로그인 후 조회 차단",
    (await former.raw("GET", `/api/security-requests/${id}`)).status === 404,
  );
  await admin.call(
    "PUT",
    `/api/members/${report.accounts.oldsecurity}/activation`,
    { active: false, reason: "합성 휴직" },
  );
  ok(
    "비활성 기존 세션 401",
    (await oldsecurity.raw("GET", "/api/auth/me")).status === 401,
  );
  await issue(id, "SECURITY", "ACCOUNT_INACTIVE");
  await assign(id, "SECURITY", report.accounts.newsecurity);
  await issue(id, "SECURITY", null);
  const history = await admin.call(
    "GET",
    `/api/security-requests/${id}/assignments`,
  );
  ok(
    "전임 배정 이력 비활성 보존",
    history
      .filter((a) =>
        [report.accounts.oldinfra, report.accounts.oldsecurity].includes(
          a.assigneeUsername,
        ),
      )
      .every((a) => !a.active),
  );
  const mine = await admin.call(
    "GET",
    `/api/security-requests?inbox=mine&query=${id}`,
  );
  ok("인계 완료 대상 목록에서 제외", mine.totalElements === 0);
  await admin.call(
    "PUT",
    `/api/reviewer-pools/${pools.newinfra.reviewerPoolId}/activation`,
    { active: false, reason: "합성 담당 업무 종료" },
  );
  await issue(id, "INFRA", "POOL_INACTIVE");
  await admin.call(
    "PUT",
    `/api/reviewer-pools/${pools.newinfra.reviewerPoolId}/activation`,
    { active: true, reason: "합성 업무 복귀" },
  );
  await issue(id, "INFRA", null);
  // 화면 검증용 진행 중 신청. 역할·활성 변경 및 실제 재배정은 브라우저에서 수행한다.
  await admin.call("PUT", `/api/members/${report.accounts.oldinfra}/role`, {
    role: "INFRA",
    reason: "합성 화면 검증 준비",
  });
  await admin.call(
    "PUT",
    `/api/members/${report.accounts.oldsecurity}/activation`,
    { active: true, reason: "합성 화면 검증 준비" },
  );
  const ui = await create("화면 업무 인계");
  const uid = ui.requests[0].securityRequestId;
  await start(uid);
  await assign(uid, "INFRA", report.accounts.oldinfra);
  await assign(uid, "SECURITY", report.accounts.oldsecurity);
  const uiReview = (
    await admin.call("GET", `/api/security-requests/${uid}/reviews`)
  ).find((r) => r.field === "INFRA");
  const currentOld = await client(report.accounts.oldinfra);
  await currentOld.call(
    "PUT",
    `/api/reviews/${uiReview.reviewId}/answers/system.target_server`,
    { value: "화면 인계 확인용 합성 서버" },
  );
  report.resources.uiCase = ui.businessCaseId;
  report.resources.uiRequest = uid;
  report.resources.uiReview = uiReview.reviewId;
  report.uiBefore = await admin.call(
    "GET",
    `/api/security-requests/${uid}/reviews`,
  );
} finally {
  for (const p of defaults)
    await admin.call("PUT", `/api/reviewer-pools/${p.reviewerPoolId}/default`);
  await writeFile(
    process.env.VERIFY_OUTPUT ?? "handoff-verification.json",
    JSON.stringify(report, null, 2),
  );
}
console.log(`합성 검증 통과: ${report.checks.length}개 · ${report.prefix}`);
console.log(JSON.stringify(report.resources));
