# Front 수정 요청서 — 같은 경로, 다른 요청·응답 모양 (2026-11-09)

받는 쪽: Security-Advisor-Front 개발 AI. 기준: Back `main` (12d89ed 이후 프론트 호환 API 8종 추가분 포함), `docs/api/openapi.yaml`·`docs/FRONTEND_HANDOFF.md` 6장.
원칙: **계약 소유자는 Back** 이다. 아래 항목은 Front 의 Zod 스키마·훅·어댑터를 Back 응답에 맞추는 작업이며, Back 은 바꾸지 않는다. 화면 표시용 가공은 어댑터 한 곳(`features/*/adapters`)에서만 하고, 서버 판단(권한·전이 가능 여부)을 클라이언트에서 만들어내지 않는다.

## 1. 공통 — 이름·타입 규칙

| Front 현재 | Back 실제 | 조치 |
|---|---|---|
| `id: string` | `<단수형>Id: number` (`businessCaseId`·`securityRequestId`·`reviewId`·`supplementId`·`assignmentId`·`deadlineId`·`submissionId`·`auditEventId`) | 스키마를 `z.number()` 로, 경로 변수는 숫자 문자열. 화면용 `id` 가 필요하면 어댑터에서 복사 |
| 사람 식별 `actorId`·`ownerId`·`assigneeId`·`userId`(+ `*Name`) | `*Username` 문자열만(`applicantUsername`·`assigneeUsername`·`requestedBy`·`completedBy`·`confirmedBy`·`actorUsername`) | 표시 이름이 필요하면 `GET /api/members`(관리자) 또는 자기 `GET /api/auth/me` 로 조회. 서버가 `*Name` 을 주지 않음 |
| 시각 `at`·`createdAt` 문자열 | `Instant` ISO-8601 UTC(`…Z`) | 그대로 수신, 표시 시 KST 변환 |
| 목록 `{content,page,size,totalElements}` | Spring `Page` 직렬화: `content`·`page{size,number,totalElements,totalPages}` | 페이지 메타를 `page.*` 에서 읽기 |

## 2. 답변(Answer) — `answerSchema`

| Front | Back(`AnswerResponse`) | 조치 |
|---|---|---|
| `key` | `fieldKey` | 이름 변경 |
| `source` | `valueSource` (USER_ANSWER/AI_INFERRED/STAFF_INPUT/STAFF_CONFIRMED, null 가능) | 이름 변경 |
| `confirmation{actorId,actorName,role,at}` | `confirmedBy`(username)·`confirmedAt` | 평탄화. role 은 없음 |
| `label`·`ownerRole`·`required`·`notApplicable` | 응답에 없음 — 게시본 질문 스키마(`GET /api/publications/current` 의 `fields`)에서 `fieldKey` 로 조인 | 어댑터에서 게시본과 병합 |
| — | `answerId`·`needsReview`·`revision` 추가 | `needsReview` 는 "공통 정보 변경 후 재검토 필요" 표시에 사용. `revision` 은 답변 단위 `expectedRevision` 에 사용 |

## 3. 사업 건 — `GET /api/business-cases/{id}`

| Front | Back(`BusinessCaseResponse`) |
|---|---|
| `description` | `summary` |
| `ownerId`/`ownerName` | `applicantUsername` |
| `publicationVersion` | `publicationId` |
| `answers` | `caseAnswers` |
| — | `requests: SecurityRequestSummary[]`·`progressSummary` 추가(목록 화면에 바로 사용) |

공통 답변 일괄 저장은 `PATCH /api/business-cases/{id}` 가 아니라 **`PATCH /api/business-cases/{id}/answers`**, body `{ "answers": [{ "fieldKey", "value", "expectedRevision"? }] }`. 제목·개요 수정은 `PUT /api/business-cases/{id}`.

## 4. 신청 — `GET /api/security-requests/{id}`

Back 응답은 **신청 본체만**(`securityRequestId, businessCaseId, docType, stage, status, statusLabel, revision, answers, createdAt, updatedAt`). Front 가 한 응답에 기대하는 집계는 각각 별도 조회다.

| Front 필드 | Back 조회 |
|---|---|
| `commonAnswers`·`caseRevision`·`caseTitle`·`department`·`ownerName` | `GET /api/business-cases/{businessCaseId}` |
| `reviews` | `GET /api/security-requests/{id}/reviews` |
| `assignments` | `GET /api/security-requests/{id}/assignments` |
| `supplements` | `GET /api/security-requests/{id}/supplements` |
| `returns` | `GET /api/security-requests/{id}/stage-returns` |
| `deadlines` | `GET /api/security-requests/{id}/deadlines` |
| `submissions`·`currentSubmissionId` | `GET /api/security-requests/{id}/submissions` (`latest: true` 인 것) |
| `finalConfirmations` | `GET /api/submissions/{submissionId}/final-confirmations` |
| `changedCommonFields`·`highlightedFields` | 답변의 `needsReview`, 최근 `stage-returns` 의 `fieldKeys`·`fieldReasons` 로 계산 |
| `blockers`·`allowedActions` | **서버가 주지 않음.** 버튼 표시는 `status`·`stage`·역할로 하되 최종 판단은 서버: 행위 요청의 `422 ProblemDetail.detail` 을 그대로 안내문으로 표시 |

TanStack Query 로 위 조회를 `useQueries` 묶음으로 두고, 화면 타입 `SecurityRequest` 는 어댑터가 조립한다. 목록은 `GET /api/security-requests?status&docType&page&size` → `SecurityRequestListItem`(`caseTitle`·`department`·`applicantUsername` 포함).

## 5. 열거형 값

| 항목 | Front | Back |
|---|---|---|
| 신청 상태 | `DRAFT, CONSULTING, SUPPLEMENT_REQUESTED, FINAL_REVIEW, SUBMITTED, FINALIZED, CLOSED, WITHDRAWN` | `DRAFTING, IN_REVIEW, SUPPLEMENT_REQUESTED, FINAL_REVIEW, SUBMITTED, CONFIRMED, CLOSED, WITHDRAWN` (+ `statusLabel` 한글) |
| 협의 상태 | `PENDING, ALLOCATED, IN_REVIEW, SUPPLEMENT_REQUESTED, COMPLETE, RECHECK` | `WAITING, ALLOCATED, IN_PROGRESS, SUPPLEMENT_REQUESTED, COMPLETED, RECHECK` |
| 기한 종류 | `CONSULTATION, SUPPLEMENT, FINALIZATION` | `REVIEW, SUPPLEMENT_RESPONSE, FINAL_CONFIRMATION` |
| 기한 상태 | `timing: WITHIN, DUE_SOON, OVERDUE, CLOSED` | `status: ON_TRACK, IMMINENT, OVERDUE, ENDED` (+ `dueDate` LocalDate, `paused`) |
| 서류 유형 | `z.enum(['FIREWALL','SECURITY_REVIEW'])` | 문자열 코드(게시본에 따라 추가 가능) → `z.string().regex(/^[A-Z][A-Z0-9_]{1,29}$/)` |

표시 문구는 서버의 `statusLabel` 을 우선 사용하고, 색상 매핑만 Front 에 둔다.

## 6. 협의·보완·반환·배정 body

| 행위 | Front | Back |
|---|---|---|
| 협의 완료 `PUT /api/reviews/{id}/completion` | `comment` | `{ "opinion", "checklist": {항목: boolean}, "expectedRevision" }` |
| 보완 요청 | `POST /api/security-requests/{id}/supplements` | **`POST /api/reviews/{reviewId}/supplements`** `{ fieldKeys[], reason, expectedRevision }` (조회는 `GET /api/security-requests/{id}/supplements` 그대로) |
| 보완 답변 `POST /api/supplements/{id}/answers` | `answer` | `{ "note", "expectedRevision" }` → 응답 `answerNote`·`answeredAt` |
| 보완 해소 `PUT /api/supplements/{id}/resolution` | — | `{ "note"?, "expectedRevision" }`, 그 분야 배정 담당자만 |
| 차수 반환 `POST …/stage-returns` | `fromStage, targetStage, fieldKeys, reason` | `{ targetStage(1~3), reason, fieldKeys[], fieldReasons{fieldKey: 사유}, expectedRevision }` → 응답 `fromStage`·`toStage`·`returnedBy` |
| 재배정 `POST …/assignments` | `assigneeId` | `{ field, assigneeUsername, reason }` |
| 최종 확인 `POST /api/submissions/{id}/final-confirmations` | — | `{ opinion?, expectedRevision }` |
| 제출 `POST …/submissions` | body 에 `idempotencyKey` | **헤더** `Idempotency-Key`(body 없음). 201 신규 / 200 같은 키 재요청. 응답 `sequenceNo`·`latest`·`snapshot`(JSON) |

## 7. 분석 작업

Front `POST /api/business-cases/{id}/analysis-jobs`(multipart text+file) 대신 2단계: `POST /api/business-cases/{id}/uploaded-files`(multipart `file`, PDF/DOCX/HWPX/TXT; HWP 는 "HWPX 로 변환해 올려 주세요" 안내) → `POST /api/business-cases/{id}/analysis-jobs { uploadedFileId }` 202 → `GET /api/analysis-jobs/{id}` 폴링(`status` QUEUED/RUNNING/NEEDS_REVIEW/FAILED). 직접 입력 개요는 `PUT /api/business-cases/{id}` 의 `summary`.
`POST /api/analysis-jobs/{id}/applications { expectedRevision }` 의 응답은 사업 건이 아니라 `AnalysisApplicationResult { job, summaryProposal(null 가능), proposals[{securityRequestId, result}], warnings[] }` 이며, 반영된 값은 전부 `AI_INFERRED`·미확정이다. 사용자가 이미 확인한 `project.summary` 는 덮어쓰지 않고 `warnings` 로 알린다.

## 8. 인증·계정

- `GET /api/auth/me`·로그인 응답 `MemberResponse { username, displayName, role, department, email, expiresAt }`. `Session` 어댑터의 `department=''`·`expiresAt=null` 하드코딩 제거. `active` 는 응답에 없음(비활성 계정은 로그인 자체가 거부됨).
- `POST /api/sessions/current/renewals` → 201 `{ expiresAt }`.
- `POST /api/members/{username}/role-assignments { role, active?, reason }`(201), 활성 상태만 바꿀 때는 `PUT /api/members/{username}/activation { active, reason }`. `{id}` 는 숫자가 아니라 `username`.
- `POST /api/demo-sessions` 는 제공하지 않음. 모의 모드(`mode=mock`)에서만 쓰고 API 모드에서는 로그인 화면으로.

## 9. 감사 `GET /api/audit-events`

`actorId/actorName` → `actorUsername`, `fieldKeys` 없음(`summary` 문자열에 포함), `requestId` 는 HTTP 요청 추적 ID. 신청별 이력은 `GET /api/security-requests/{id}/audit-events`.

## 확인 요청

1. 위 변경 후 `mode=api` 로 실제 Back(`compose.backend-test.yaml`)에 붙여 로그인 → 사업 건 생성 → 신청 생성 → 답변 일괄 저장 → 협의 요청 → 제출(멱등) 흐름을 한 번 실행하고 결과를 PR 에 기록해 주세요.
2. 모의 서버(`mocks/server.ts`)도 같은 모양으로 맞춰 두 모드의 Zod 스키마가 하나가 되게 해 주세요.
3. 맞출 수 없거나 Back 에 요청하고 싶은 항목은 이 문서 번호로 Back 저장소 이슈에 남겨 주세요.
