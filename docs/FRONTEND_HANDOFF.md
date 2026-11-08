# 프론트 전달 안내 (Security-Advisor-Back → Security-Advisor-Front)

2026-11-08, 커밋 3fe4254 기준. 백엔드 1차 범위(F-01~F-17)가 모두 들어갔고 테스트 135건이 통과한 상태다. 이 문서는 프론트가 백엔드를
붙일 때 알아야 할 것만 모았다. API 하나하나의 의미는 `README.md` 표, 정확한 요청·응답 스키마는 `docs/api/openapi.yaml`(생성물, 원본)이다.

## 1. 받는 것

| 파일 | 용도 |
|---|---|
| `docs/api/openapi.yaml` | API 계약. 모의 서버·타입 생성의 기준. 손으로 고치지 않는다(계약 변경은 Back PR 에서 재생성) |
| `README.md` | API 표(한국어 설명·상태 코드), ERD, 실행 절차 |
| `compose.yaml` + `.env.example` | 로컬에서 백엔드를 Docker 로 띄우는 방법 |
| `docs/ARCHITECTURE.md` | 구조·흐름(참고용) |

실행 중인 서버에서는 `/v3/api-docs.yaml`, `/swagger-ui.html` 로도 볼 수 있다.

## 2. 로컬 실행

```
cp .env.example .env      # DEMO_ACCOUNT_PASSWORD 등 시연 값 포함(전부 합성)
docker compose up --build # db(PostgreSQL 17) + app, http://localhost:8080
```

- 시연 계정(비밀번호는 `.env` 의 `DEMO_ACCOUNT_PASSWORD`): `applicant`·`applicant2`(신청자), `infra`(ICT인프라, INFRA 기본 담당자), `security`(정보보안, SECURITY 기본 담당자), `admin`(기관 관리자).
- AI 없이 돌리기: `.env` 에 `LLM_MODE=fake` → 고정 응답(네트워크 불필요). 실제 모델: `OPENROUTER_API_KEY` 만 넣으면 seed 구성(DeepSeek V4.1 Flash, OpenRouter)이 선택된다. 키는 `.env` 에만, 절대 코드·채팅에 넣지 않는다.
- 초기화: `docker compose down -v` 후 재기동하면 seed 상태로 돌아간다.
- 프론트 개발 서버가 다른 포트면 같은 출처가 아니므로 **프록시**(Next.js rewrites 로 `/api` → `localhost:8080`)를 쓴다. 백엔드에 CORS 설정은 없다(같은 출처 전제 — 배포도 compose 에서 한 출처로 묶는다).

## 3. 공통 규약

### 인증·세션
- `POST /api/auth/login` `{username,password}` → 세션 쿠키 발급. `GET /api/auth/me` → `{username, displayName, role}`. 미로그인·만료는 **401**(세션 30분, `SESSION_TIMEOUT`).
- 로그인 연속 실패 5회 → **429**(15분 잠금). 메시지를 그대로 보여 준다.
- `role` 은 메뉴·화면 분기에만 쓴다. 권한 판단은 서버가 하며 거부는 **403**.
- 회원가입 `POST /api/members` 는 익명 허용, 역할은 항상 APPLICANT(요청에 role 을 보내도 무시).

### CSRF
- 상태를 바꾸는 요청(POST·PUT·PATCH·DELETE)은 `XSRF-TOKEN` 쿠키 값을 **`X-XSRF-TOKEN` 헤더**로 보낸다. 로그인·회원가입도 포함.
- 쿠키는 아무 GET 응답(401 이어도)에 실려 온다. 앱 시작 시 `GET /api/auth/me` 한 번 호출하면 된다. 없거나 틀리면 403.

### 오류
- 모든 오류는 RFC 9457 ProblemDetail JSON: `{"status":422,"title":"…","detail":"…"}`. `detail` 은 사용자에게 그대로 보여 줄 수 있는 한국어 문장이다(내부 정보 없음).

| 상태 | 뜻 | 화면 처리 |
|---|---|---|
| 400 | 형식 오류(필수 누락·잘못된 열거형 값·Idempotency-Key 없음) | 입력 검증 메시지 |
| 401 | 미로그인·세션 만료 | 로그인 화면으로 |
| 403 | 권한 없음·CSRF 실패 | 안내 후 머무름 |
| 404 | 없거나 남의 자원(신청자가 남의 신청을 볼 때도 404) | "찾을 수 없음" |
| 409 | `expectedRevision` 불일치, 중복(아이디 등) | 최신 값 다시 읽고 사용자가 재시도. 입력값은 버리지 않는다 |
| 422 | 업무 규칙 위반(제출 조건·단계 불일치·형식 불일치 등) | `detail` 그대로 표시 |
| 429 | 로그인 잠금 | `detail` 표시 |
| 503 | AI 모델 호출 실패·미구현 출력 형식 | "AI 사용 불가 — 직접 입력/재시도" 제공(실패를 성공처럼 보이게 하지 않는다) |

### 동시성(revision)
- 수정 요청은 대상의 현재 `revision` 을 `expectedRevision` 으로 같이 보낸다(신청·협의·답변·설정·사업 건 각각 자기 revision). 응답에 새 revision 이 온다. 409 면 다시 읽는다.

### 값의 출처·확인 상태(화면 표기 필수)
- 모든 답변(`AnswerResponse`)은 `valueSource`(USER_ANSWER / AI_INFERRED / STAFF_INPUT / STAFF_CONFIRMED / null) 와 `confirmed`, `needsReview` 를 가진다. AI 값은 "AI 제안(미확정)"으로 표시하고 확정처럼 보이게 하지 않는다. `null` 출처는 "담당자 확인 필요".
- 상태 용어 구분: 협의 `ALLOCATED`(할당 완료) ≠ `COMPLETED`(협의 완료), 신청 `CONFIRMED`(서류 확정) ≠ 방화벽 적용(`/api/firewall-rules` 의 별도 상태).

## 4. 화면별 주요 API (경로는 openapi.yaml 기준)

| 화면 | API |
|---|---|
| 사업 건 목록·생성 | `GET/POST /api/business-cases`, `GET /api/business-cases/{id}`(공통 답변 `caseAnswers`, 하위 신청 `requests`, `progressSummary`), 서류 추가 `POST /api/business-cases/{id}/security-requests`, **AI 서류 유형 제안** `POST /api/doc-type-suggestions {summary}`(생성 화면에서 "어떤 서류가 필요한지 모르겠어요" → 제안 목록 + 근거, 체크는 사용자) |
| 문서 업로드·AI 요약 | `POST /api/business-cases/{id}/uploaded-files`(multipart `file`, pdf·docx·hwpx·hwp·txt·md 20MB; 내용 서명 불일치 422, 암호·배포용 HWP 는 분석 FAILED 메시지에 HWPX 변환 안내) → `POST …/analysis-jobs`(202 + `Location`) → `GET /api/analysis-jobs/{id}` 폴링(`QUEUED→RUNNING→NEEDS_REVIEW/FAILED`) |
| 인터뷰(공통 → 서류별) | `GET /api/publications/{id}`(질문 스키마 `fieldSchema.fields`, `scope` CASE/REQUEST, `askRole`), `PUT /api/business-cases/{id}/answers/{key}`, `PUT /api/security-requests/{id}/answers/{key}`, 항목 도움말 `GET /api/field-help/{key}?docType=`, AI 제안 `POST /api/security-requests/{id}/ai-proposals`, 확인 `PUT …/answers/{key}/confirmation`, 담당자 확인 요청 `PUT …/answers/{key}/staff-check` |
| 협의 요청·진행 | `POST /api/security-requests/{id}/reviews`, `GET …/reviews`, `GET …/assignments`, `GET …/deadlines`(`status` ON_TRACK·IMMINENT·OVERDUE, 보완 대기 중 협의 기한은 `paused:true` 로 종료되고 답변 뒤 새 행), `GET …/audit-events`(처리 이력 탭) |
| 담당자 요청함 | `GET /api/reviews?status&requestStatus&field&docType&keyword`(한 줄에 `review`·`request`·`caseTitle`·`reviewDueDate`·`openSupplementCount`·`siblingRequests`), `GET /api/todo-summary`(배지) |
| 담당자 검토 | `PUT /api/reviews/{id}/answers/{key}`(STAFF_INPUT), `…/confirmation`, `…/allocation-completion`(INFRA), `…/completion`(체크리스트·의견), 보완 `POST /api/reviews/{id}/supplements`, 차수 반환 `POST /api/security-requests/{id}/stage-returns`(`fieldKeys`·`fieldReasons` → 반환 화면에서 항목 강조 + 항목별 사유), 종결 `PUT /api/security-requests/{id}/closure`, AI 질의 `POST /api/security-requests/{id}/inquiries` → `POST /api/inquiries/{id}/messages` |
| 신청자 보완·철회 | `GET /api/security-requests/{id}/supplements`, `POST /api/supplements/{id}/answers`, `GET …/stage-returns`, `PUT …/withdrawal` |
| 서류·제출 | `POST /api/security-requests/{id}/generated-documents`(`aiSupplement:true` 가능), `GET /api/generated-documents/{id}`(섹션 JSON 으로 미리보기), 다운로드 `GET …/download?format=MARKDOWN|PDF`(`Content-Disposition` 파일명 UTF-8), 형식 목록 `GET /api/document-export-formats`(`implemented:false` 는 "준비 중"), 제출 `POST /api/security-requests/{id}/submissions`(헤더 `Idempotency-Key` 필수, 422 `detail` 에 미충족 조건) → 완료 화면에 `submissionId`·`sequenceNo`·`submittedAt` |
| 최종 확인·확정(담당자) | `GET /api/submissions?latestOnly=&docType=`, `GET /api/submissions/{id}`(스냅샷), `POST /api/submissions/{id}/final-confirmations`, `PUT /api/submissions/{id}/confirmation` |
| 정책 수명주기 | `GET /api/firewall-rules?effectiveStatus=`(`effectiveStatus` PENDING_APPLY·APPLIED·NOT_APPLICABLE·REVOKED + 계산 상태 EXPIRING·EXPIRED), `GET /api/security-requests/{id}/firewall-rules`, `PUT /api/firewall-rules/{id}/application`·`/revocation`(INFRA), **연장** `POST /api/firewall-rules/{id}/renewal-requests {newPeriod, note}`(신청자, 만료 예정 규칙 카드의 "연장 신청" 버튼 → 응답의 `request.securityRequestId` 로 새 신청 화면 이동) |
| 알림 | `GET /api/notifications?unreadOnly=`, `GET …/unread-count`, `PATCH /api/notifications/{id}` `{"read":true}`. `link` 는 API 경로이므로 화면 라우트로 바꿔 쓴다 |
| 기관 관리자 | 계정·역할 `GET /api/members`, `PUT /api/members/{username}/role`, 연락처 `PUT /api/members/{username}/contact {email}`(본인도 가능 — 내 정보 화면); 외부 알림 채널 상태 `GET /api/notification-channels`(꺼져 있으면 "설정 필요" 표시); 담당자 목록 `GET/POST /api/reviewer-pools`, `PUT …/{id}/default`, `PUT …/{id}/activation`(응답 `reassignmentNeededRequestIds`), 재배정 `POST /api/security-requests/{id}/assignments`; 설정 편집 `GET /api/configurations/current` → `PUT /api/configurations/{id}` → `POST …/publications`; 지식 `GET/POST/PUT /api/knowledge-snippets`, `/api/private-contexts`; LLM 구성 `/api/llm-configurations`(키는 환경변수 이름만); 기한 알림 스케줄 `GET/PUT /api/deadline-reminder-schedule`, 수동 실행 `POST /api/deadline-reminder-runs`; 지표 `GET /api/insights/summary`; 감사 `GET /api/audit-events` |
| 머리글 | `GET /api/installation`(기관명·코드, 로그인 전 허용) |

## 5. 화면 표기 원칙(design.md·불변 원칙과 연결)

- AI 값은 "제안"이며 사람이 확인해야 확정이다. `confirmed=false` 인 값은 제출 전 반드시 표시한다(제출 422 의 `detail` 이 미충족 항목을 알려 준다).
- 차수 반환 화면은 기존 값을 그대로 두고 `fieldKeys` 항목만 강조하며 `fieldReasons` 를 항목 옆에 보여 준다.
- 기한 경과(`OVERDUE`)는 표시·알림일 뿐 상태를 바꾸지 않는다.
- 시연 수치(지표)는 "합성 데이터 기준"을 화면에 적는다. 서류 PDF 바닥글에도 이미 들어 있다.
- 미구현 기능(HWPX 출력, 메일 알림 등)은 "준비 중"으로 보이게 한다. 구현된 것처럼 보이는 버튼을 두지 않는다.

## 6. 백엔드에 물어볼 것·바꿀 것이 생기면

- 계약 변경은 Back 저장소 이슈/PR 로. `openapi.yaml` 은 Back 이 재생성한다.
- 모르는 422 메시지가 나오면 `README.md` 표의 "응답" 열에 조건이 적혀 있다.
