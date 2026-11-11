# Security-Advisor-Back

보안길잡이(SecurityAdvisor) 백엔드 — Spring Boot 4.1 · Java 25 · PostgreSQL 17 · Flyway · Gradle(Kotlin DSL) · Lombok(허용 목록만, `lombok.config`로 강제).

처음 받았다면 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)부터 읽는다 — 구조, 주요 흐름, 설계 결정, 명세와 다른 점, 기능별 동작 규칙이 한 문서에 있다.

| 문서 | 내용 |
|---|---|
| 이 README | 실행 방법, API 표, DB 명세·ERD |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 구조·흐름·설계 결정·명세와 다른 점 |
| [docs/api/openapi.yaml](docs/api/openapi.yaml) | API 계약(생성물 — [생성 방법](docs/api/README.md)) |
| [docs/knowledge/](docs/knowledge/README.md) | 공개 지식 조각 데이터와 등록 방법 |
| [FUNCTIONAL_SPEC.md](FUNCTIONAL_SPEC.md) | 업무 규칙 원본(2026-11-07 사본 — 구현과 다른 곳은 ARCHITECTURE 5장) |
| [AGENTS.md](AGENTS.md) · [CODING_STYLE.md](CODING_STYLE.md) · [SECURE_CODING.md](SECURE_CODING.md) | 개발·코드 작성 규칙(2026-11-07 사본) |

규칙 문서 사본 안의 PROJECT_CONTEXT·PROJECT_RULES·CONTRIBUTING·design·INSTRUCTIONS 링크는 이 저장소에 없는 팀 문서를 가리킨다.

담당자는 현재 역할의 활성 배정 신청만 조회하며, 기관 통계는 관리자 전용이다. 계정 비활성화·역할 변경 후 기존 세션은 재로그인이 필요하다. 계정·역할·분야 등록이 바뀌면 진행 중 신청에 재배정 필요를 표시하고, 관리자가 사유를 남겨 후임에게 인계한다(ARCHITECTURE 4장 0010, 6장).


## 준비물

- JDK 25 (없으면 Gradle toolchain이 자동으로 내려받는다)
- Docker (개발용 DB와 테스트의 Testcontainers에 필요)
- PostgreSQL 17 — `jdbc:postgresql://localhost:5432/boan` (계정 `boan`/`boan`, `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`로 변경)

## 실행

```bash
cp .env.example .env         # 시연 계정 비밀번호(DEMO_ACCOUNT_PASSWORD) 등 — .env 는 커밋하지 않는다
./gradlew bootRun            # http://localhost:8080 , Swagger UI: /swagger-ui.html
./gradlew spotlessApply check  # 커밋 전 필수: 포맷 + 테스트 + ArchUnit
```

기동 시 `DemoAccountSeeder` 가 역할별 시연 계정 5건(`applicant`·`applicant2`·`security`·`infra`·`admin`, 비밀번호는 `DEMO_ACCOUNT_PASSWORD`)을 bcrypt 해시로 만든다. 비밀번호를 비우면 만들지 않는다. 마이그레이션에는 계정을 넣지 않는다.

### Docker 만으로 실행 (compose)

```bash
cp .env.example .env            # 시연 비밀번호·기관명·LLM 키(선택) 채우기
docker compose up -d --build    # db(PostgreSQL 17) + app
# http://localhost:8080/swagger-ui.html
docker compose --profile ollama up -d --build   # 공개 모델(Ollama) 도 함께 — 이후 docker compose exec ollama ollama pull exaone3.5:7.8b
docker compose down -v          # 시연 데이터 초기화
```

완성본·제출용 실행은 Security-Advisor 저장소의 compose(Back + Front + DB)가 맡는다. CI(`.github/workflows/check.yml`)는 push·PR 마다 `./gradlew check` 를 돌린다.
API 계약 파일은 `docs/api/openapi.yaml`(생성 방법은 `docs/api/README.md`).

## API

경로와 한 줄 설명이다. 요청·응답 스키마는 [docs/api/openapi.yaml](docs/api/openapi.yaml)(실행 중에는 `/swagger-ui.html`), 동작 규칙은 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 6장에 있다. 아래 표의 경로 146개는 컨트롤러 코드와 기계적으로 대조했다(설명 문구는 사람이 썼다).

### 공통 규약

- **인증:** 세션 쿠키. 로그인·회원가입·`GET /api/installation` 외에는 모두 로그인이 필요하다. 미로그인·만료는 401.
- **CSRF:** 상태를 바꾸는 요청은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보낸다.
- **권한:** 서버가 판단한다. 역할이 맞지 않으면 403, 볼 수 없는 자원(남의 신청·배정되지 않은 신청)은 존재를 숨겨 404.
- **오류:** 전부 ProblemDetail(JSON). 400 형식 · 401 · 403 권한 · 404 없음/조회 불가 · 409 revision 불일치·중복 · 422 업무 규칙 위반 · 429 로그인 잠금 · 503 AI 모델 사용 불가. 422·503 의 `detail` 은 사용자에게 보여 줄 문장이다.
- **동시 변경:** 쓰기 요청은 `expectedRevision` 을 받는다(처음 채우는 답변 제외). 다르면 409 — 최신 값을 다시 읽고 사용자가 판단한다.
- **상태 전이:** `status`·`stage` 를 직접 바꾸는 API 는 없다. 하위 자원 POST(협의 요청·보완·반환·제출) 또는 단일 하위 자원 PUT(협의 완료·종결·철회·확정)으로 한다.
- **값의 출처:** 모든 답변은 `valueSource`(USER_ANSWER·AI_INFERRED·STAFF_INPUT·STAFF_CONFIRMED)와 `confirmed` 를 가진다. AI 값은 사람이 확인하기 전까지 미확정이고, 미확정 값이 남아 있으면 제출이 막힌다.
- **목록:** 페이지형은 `{content, page, size, totalElements}`(`page` 는 0부터, `size` 최대 100).

### 인증·계정 API

| 요청 | 설명 | 응답 |
|---|---|---|
| `POST /api/members` | 회원가입(익명). 아이디·비밀번호·표시 이름·부서(선택). 역할은 요청과 무관하게 APPLICANT | 201, 중복 409, 형식 400 |
| `POST /api/auth/login` | 로그인 — 세션 쿠키 발급, 사용자 정보 반환 | 200, 실패 401, 잠김 429 |
| `POST /api/auth/logout` | 로그아웃. 로그인하지 않은 상태여도 204 | 204 |
| `GET /api/auth/me` | 현재 사용자(`department`·`email`·세션 만료 예정 `expiresAt` 포함) | 200, 미로그인·만료·세션 회수 401 |
| `POST /api/sessions/current/renewals` | 세션 연장 — 새 `expiresAt` 을 돌려준다 | 201 |
| `GET /api/members` | 계정 목록(관리자). `role` 로 거를 수 있다. 해시·실패 횟수는 내보내지 않는다 | 200, 관리자 아님 403 |
| `GET /api/members/{username}` | 계정 상세(관리자) | 200, 403, 404 |
| `PUT /api/members/{username}/role` | 역할 부여·변경(관리자, `role`·`reason`). 멱등. 실제로 바뀌면 그 계정의 기존 세션이 끊긴다 | 200, 자기 자신·마지막 활성 관리자 강등 422 |
| `PUT /api/members/{username}/activation` | 계정 활성/비활성(관리자, `active`·`reason`). 비활성 계정은 로그인 불가·자동 배정 제외, 기존 배정은 "재배정 필요"로 표시 | 200, 자기 자신·마지막 관리자 422 |
| `POST /api/members/{username}/role-assignments` | 역할 + 활성 상태를 한 번에(관리자 화면용) | 201, 위와 같음 |
| `PUT /api/members/{username}/contact` | 메일 알림 수신 주소(본인 또는 관리자). 비우면 해제 | 200, 형식 400 |
| `GET /api/installation` | 운영 기관 표시 정보(`INSTITUTION_NAME`·`INSTITUTION_CODE`). 인증 없음 | 200 |

- **CSRF:** 상태를 바꾸는 요청(POST·PUT·PATCH)은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보낸다(로그인·회원가입 포함). 쿠키는 아무 GET 응답(401 이어도)에 실려 온다. 없거나 틀리면 403.
- **로그인 실패 제한:** 연속 `LOGIN_MAX_FAILURES`(기본 5)회 실패하면 `LOGIN_LOCK_DURATION`(기본 15분) 동안 429.
- **세션 회수:** 계정의 역할·활성 상태가 바뀌면 `session_version` 이 올라가고, 그 계정의 기존 세션은 다음 요청에서 401 이 된다(다시 로그인).
- 기동 시 `DemoAccountSeeder` 가 시연 계정 5건(`applicant`·`applicant2`·`security`·`infra`·`admin`)을 만든다. 비밀번호는 `DEMO_ACCOUNT_PASSWORD`, 비어 있으면 만들지 않는다.

### 사업 건·신청·답변 API

| 요청 | 설명 | 응답 |
|---|---|---|
| `GET /api/publications/current` | 현재 설정 게시본 — 서류 유형 목록(`docTypes`)과 질문 항목(`fields`). 서류 유형은 코드가 아니라 이 데이터가 정한다 | 200 |
| `GET /api/publications/{id}` | 게시본 하나(사업 건이 고정한 버전 조회용) | 200, 404 |
| `POST /api/doc-type-suggestions` | **AI 서류 유형 제안** `{summary?, businessCaseId?}` — 개요·문서 요약을 근거로 활성 유형 중 필요한 것을 제안(선택은 사용자) | 200, 둘 다 없음 400, 모델 실패 503 |
| `POST /api/business-cases` | 사업 건 + 선택한 서류 유형별 신청 생성. 그 시점의 설정 게시본·지식 게시본에 고정. 사업명·개요·부서는 공통 답변으로, 담당자 항목은 "담당자 확인 필요" 자리로 미리 생성 | 201, 유형 없음 400, 게시본에 없는 유형 422 |
| `GET /api/business-cases` | 내 사업 건 목록(`page`·`size`, 진행 요약은 하위 신청에서 계산) | 200 |
| `GET /api/business-cases/{id}` | 사업 건 상세 + 공통 답변 + 조회 가능한 신청 요약 | 200, 남의 것 404 |
| `PUT /api/business-cases/{id}` | 사업 개요 수정(`expectedRevision`) | 200, 불일치 409 |
| `POST /api/business-cases/{id}/security-requests` | **기존 사업 건에 서류 추가** `{docType}` — 공통 답변·문서를 공유하는 새 신청이 1단계로 생긴다(게시본은 사업 건 생성 시점 것) | 201, 같은 유형의 진행 중 신청 있음 422 |
| `PUT /api/business-cases/{id}/answers/{fieldKey}` | 공통 항목 답변. 그 항목의 작성 역할만 입력할 수 있고 값은 항목 형식 검사를 받는다. 선택 항목은 `notApplicable:true`(미해당) 가능 | 200, 항목 없음 404, 권한 403, 형식·범위 422, revision 409 |
| `PATCH /api/business-cases/{id}/answers` | 공통 답변 일괄 저장 `{answers:[{fieldKey,value,expectedRevision?}]}` — 한 트랜잭션(하나라도 실패하면 전부 취소) | 200, 위와 같음 |
| `PUT /api/business-cases/{id}/answers/{fieldKey}/confirmation` | 공통 항목의 AI 제안 값을 사용자가 확인 | 200 |
| `PUT /api/business-cases/{id}/answers/{fieldKey}/staff-check` | 공통 항목을 담당자 확인 요청으로 넘김 | 200 |
| `GET /api/security-requests` | **신청 목록(권한별 검색)** — 신청자는 자기 신청, 담당자는 현재 역할로 활성 배정된 신청, 관리자는 전체. `query`·`status`·`docType`·`businessCaseId`·`from`·`to`(생성일, KST)·`inbox`·`documentKind`·`supplementState`·`sort`·`page`·`size`. DB 에서 필터·정렬·페이지 처리하고 `summary`(업무함 선택 전 건수)를 함께 준다 | 200, 잘못된 값 400 |
| `GET /api/security-requests/{id}` | 신청 상세 + 서류별 답변 | 200, 조회 권한 없음 404 |
| `PUT /api/security-requests/{id}/answers/{fieldKey}` | 서류별 항목 답변(규칙은 공통 항목과 같음) | 200, 제출·종결·철회 후 422 |
| `PATCH /api/security-requests/{id}/answers` | 서류별 답변 일괄 저장 | 200 |
| `PUT /api/security-requests/{id}/answers/{fieldKey}/confirmation` | 서류별 항목의 AI 제안 값을 사용자가 확인 | 200 |
| `PUT /api/security-requests/{id}/answers/{fieldKey}/staff-check` | 서류별 항목을 담당자 확인 요청으로 넘김 | 200 |
| `PUT /api/security-requests/{id}/withdrawal` | 신청 철회(사유 필수) | 200, 종료 상태 422 |

- 답변 형식: TEXT·LONG_TEXT·BOOLEAN·NUMBER·DATE_RANGE·IP_ADDRESS·PORT·PROTOCOL. 규칙은 `docs/ARCHITECTURE.md` 6장 "답변 권한과 형식".
- 사용자가 답변을 바꾸면 2단계에 있는 미제출 신청의 **완료된 협의가 재검토로** 돌아가고, 확인해 둔 최종 서술은 무효가 된다.

### 문서 업로드·AI 요약·인터뷰·AI 제안 API

| 요청 | 설명 | 응답 |
|---|---|---|
| `POST /api/business-cases/{id}/uploaded-files` | **문서 업로드**(multipart `file`): pdf·docx·hwpx·hwp·txt·md, 20MB. 확장자뿐 아니라 실제 내용 서명을 검사한다. 신청자 본인·미제출 신청이 있는 사업 건만 | 201, 형식·크기·내용 불일치 422 |
| `GET /api/business-cases/{id}/uploaded-files` | 업로드 파일 목록 | 200 |
| `POST /api/business-cases/{id}/analysis-jobs` | **AI 요약 접수** `{uploadedFileId}` — 202 + `Location`, 커밋 뒤 비동기 처리. 상태 `QUEUED → RUNNING → NEEDS_REVIEW`(요약은 미확정) / `FAILED` | 202 |
| `GET /api/business-cases/{id}/analysis-jobs` | 사업 건의 분석 작업 목록 | 200 |
| `GET /api/analysis-jobs/{id}` | 분석 작업 하나(상태·요약) | 200 |
| `POST /api/analysis-jobs/{id}/applications` | **분석 결과 적용**(신청자) `{expectedRevision}` — 요약을 사업 개요의 AI 제안으로 넣고 미제출 신청마다 AI 항목 제안 실행. 전부 미확정. 사용자가 확인한 값은 덮어쓰지 않고 `warnings` 로 알린다 | 201, revision 409, 완료 아님 422 |
| `GET /api/business-cases/{caseId}/interview-turns` | **신청자 인터뷰** 최근 대화(최대 30개) | 200, 남의 사업 건 404, 담당자·관리자 403 |
| `POST /api/business-cases/{caseId}/interview-turns` | 인터뷰 한 턴 `{fieldKey, mode: ASK\|SUGGEST, text, draft?, expectedRevision}` — 항목 설명 또는 답변 후보를 저장한다. 답변은 바꾸지 않는다 | 201, 담당자 항목 422, 모델 불가 503 |
| `POST /api/business-cases/{caseId}/interview-turns/{turnId}/applications` | 저장된 후보를 `AI_INFERRED`·미확정 답변으로 적용 | 200, 후보 생성 뒤 원본 변경 409 |
| `POST /api/security-requests/{id}/ai-proposals` | **AI 항목 제안**(신청자 본인, 미제출 신청) — 값이 없거나 미확정 AI 값인 신청자 항목에 대해 제안 → `AI_INFERRED`·미확정 저장. 근거 없는 제안·IP 류 토큰은 버린다(`skipped` 에 이유). body `{fieldKeys?}` | 200, 모델 불가 503, 제출 후 422 |
| `GET /api/field-help/{fieldKey}` | **항목 도움말** — 왜 묻는지·필수 여부·답하는 역할 + 그 항목에 태깅된 공개자료 근거. 모델 호출 없음. `docType`·`q`·`publicationId`·`knowledgePublicationId` | 200, 항목 없음 404 |

- 프롬프트는 [기관 사실](서버가 역할로 걸러 넣은 행, 신청자용에는 없음)과 [공개 지식](게시된 지식 조각, `S<id>` 로 인용) 블록으로 나뉜다. 모델 출력의 IP·대역이 사실 블록에 없으면 `InternalInfoGuard` 가 거른다.
- 응답이 출력 한도로 잘렸거나 비어 있으면 저장하지 않고 503 이다. 자동 재시도는 없다.

### 협의·담당자 검토·보완·반환 API

검토 행위는 **그 신청·분야에 활성 배정된 담당자**만 할 수 있다. 협의·보완·신청의 `revision` 은 각각 따로 움직이므로 요청 전에 `GET` 으로 최신 값을 읽는다.

| 요청 | 설명 | 응답 |
|---|---|---|
| `POST /api/security-requests/{id}/reviews` | **협의 요청(1→2단계)** — 분야별 협의 생성 + 기본 담당자 자동 배정 + 협의 기한 + 협의 기준본 저장, 한 트랜잭션 | 201, 1단계 아님 422, revision 409 |
| `GET /api/security-requests/{id}/reviews` | 분야별 협의 목록 | 200 |
| `GET /api/reviews` | **담당자 요청함** — 내게 활성 배정된 신청의 협의. `status`·`requestStatus`·`field`·`docType`·`keyword` | 200, 상태 이름 오류 400 |
| `GET /api/reviews/{id}` | 협의 상세(체크리스트·의견) | 200 |
| `PUT /api/reviews/{id}/answers/{fieldKey}` | **담당자 입력**(출처 STAFF_INPUT). 자기 분야 항목만. scope=CASE 항목은 사업 건에 저장된다. 2단계 협의 중인 신청만 | 200, 미배정·다른 분야 403, 2단계 아님 422 |
| `PATCH /api/reviews/{id}/answers` | 담당자 일괄 입력 — 하나라도 실패하면 전부 취소 | 200 |
| `PUT /api/reviews/{id}/answers/{fieldKey}/confirmation` | **담당자 확인**(출처 STAFF_CONFIRMED) — 자기 분야와 신청자 항목. 값은 바뀌지 않는다 | 200, 답변 없음 404 |
| `PUT /api/reviews/{id}/allocation-completion` | **쓰지 않는다.** 할당 완료는 협의 완료로 합쳐져 항상 422 를 돌려준다(경로만 남아 있음) | 200, 422 |
| `PUT /api/reviews/{id}/completion` | **분야 협의 완료** — 의견 필수, 분야 체크리스트 전부 확인, 미해결 보완 없음, 서버·통신 구성이 확인 저장 상태, SECURITY 는 INFRA 협의 완료 뒤. 필요한 분야가 모두 끝나면 신청이 3단계로 넘어가고 협의 기한이 끝난다 | 200, 규칙 위반 422, revision 409 |
| `GET /api/security-requests/{id}/infrastructure-plan` | **서버·통신 구성** 조회(서버 목록·통신 구간·운영 계획·체크리스트, `draft`·`aiPending` 상태) | 200 |
| `PUT /api/security-requests/{id}/infrastructure-plan` | 구성 임시 저장(`draft:true`, 누락 허용·형식만 검사) 또는 확인 저장(필수 항목 검사). 배정된 INFRA·SECURITY 담당자, 협의 단계. 완료된 협의는 재검토가 된다 | 200, 권한 403, 검증 422, revision 409 |
| `POST /api/security-requests/{id}/infrastructure-proposals` | **AI 구성 제안**(예: `target: SERVERS` 서버 사양) — 첨부 문서·담당자 설명과 대조한 값만 돌려주고 저장하지 않는다 | 200, 모델 불가 503 |
| `POST /api/security-requests/{id}/inquiries` | **담당자 AI 질의** 대화 생성. 그 신청의 분야에 배정된 담당자만 | 201, 미배정·신청자 403 |
| `GET /api/security-requests/{id}/inquiries` | 내 대화 목록(현재 역할의 것만) | 200 |
| `POST /api/inquiries/{id}/messages` | 질문 한 턴 — 신청 답변·업로드 원문 일부·요약·공개 지식·역할로 거른 기관 사실을 넣어 답한다. 근거 태그와 설명을 저장하고 출처 없는 IP 는 가린다. 신청 상태는 바꾸지 않는다 | 201, 잘못된 AI 응답 503(저장 안 함) |
| `GET /api/inquiries/{id}/messages` | 대화 이력 | 200, 남의 대화 404 |
| `POST /api/reviews/{id}/supplements` | **보완 요청** — 대상 항목 1개 이상 + 사유. 신청 상태 → 보완 요청(단계 유지), 대상 답변 `needsReview=true`(값 보존), 보완 응답 기한 시작, 협의 기한 정지 | 201, 완료된 협의 422 |
| `POST /api/supplements/{id}/answers` | **신청자 보완 답변** — 항목 값은 답변 API 로 먼저 고친 뒤 호출 | 201, 남의 신청 404, 본인 아님 403 |
| `PUT /api/supplements/{id}/resolution` | **보완 해소**(그 분야 배정 담당자) — 답변된 보완만, 멱등 | 200 |
| `GET /api/security-requests/{id}/supplements` | 보완 요청 목록 | 200 |
| `POST /api/security-requests/{id}/stage-returns` | **차수 반환**(2→1, 3→2, 3→1, 4→3) — 사유 필수, `fieldKeys` 로 지정한 항목만 "보완 필요" 강조(값 보존), `fieldReasons`(항목별 사유). 완료된 협의는 재검토로. 어느 분야든 배정 담당자(관리자 불가) | 201, 허용되지 않는 반환 422 |
| `GET /api/security-requests/{id}/stage-returns` | 차수 반환 이력 | 200 |
| `PUT /api/security-requests/{id}/closure` | **종결(반려)** — 사유 필수, 모든 활성 기한 종료, 이후 전이 불가. 같은 사업 건의 다른 신청은 영향 없음 | 200, 종료 상태 422 |
| `GET /api/security-requests/{id}/consultation-comparison` | **협의 결과 비교** — 협의 요청 때의 기준본, 현재 값, 그 사이 변경 기록, 최근 AI 요약 | 200 |
| `POST /api/security-requests/{id}/consultation-summaries` | **협의 결과 AI 요약**(신청자 본인, 3단계) `{expectedFingerprint}` — 근거가 연결된 요약만 저장한다. 참고용이며 확인·제출을 대신하지 않는다 | 200, 원본 변경 409 |

### 최종 서술·제출·확정·서류 API

| 요청 | 설명 | 응답 |
|---|---|---|
| `GET /api/security-requests/{id}/final-draft` | 현재 최종 서술과 확인·원본 변경 상태 | 204 / 200, 미작성 204 |
| `POST /api/security-requests/{id}/final-draft-generations` | AI 최종 서술 생성 `{expectedRevision}` — 미확정 | 201, 모델 불가 503, 경합 409 |
| `POST /api/security-requests/{id}/final-drafts` | 최종 서술 직접 작성 `{text, expectedRevision}` — 미확정 | 201 |
| `PUT /api/final-drafts/{id}/confirmation` | 본문 수정·확인 `{text, expectedRevision}`. 신청자 본인·3단계에서만. 답변이 바뀌면 확인은 무효가 된다 | 200, 단계 오류 422, 원본 변경·버전 충돌 409 |
| `POST /api/security-requests/{id}/submissions` | **제출**(3단계, 신청자 본인) + 헤더 `Idempotency-Key`. 필수값·미확정 값·형식·미해결 보완·필요 분야 협의·최종 서술 확인을 검사한다. 그 시점의 답변·구성·최종 서술·설정 버전을 불변 스냅샷으로 저장하고 최종 확정 기한을 만든다. 같은 키 재요청은 기존 제출본을 200 으로 돌려준다 | 201 / 200, 키 없음 400, 검사 실패 422(detail 에 항목 키), revision 409 |
| `GET /api/security-requests/{id}/submissions` | 신청의 제출본 개정 목록(최근 먼저, `latest` 표시) | 200 |
| `GET /api/submissions/{id}` | 제출본 상세(스냅샷 포함) | 200, 조회 권한 없음 404 |
| `GET /api/submissions` | **문서함** — 조회 권한 범위의 제출본. `docType`·`status`·`latestOnly` | 200 |
| `POST /api/submissions/{id}/final-confirmations` | **분야 최종 확인**(4단계). 배정 담당자만, 서류가 요구하는 분야 순서대로(INFRA 먼저), 분야당 1건, 최신 제출본만 | 201, 순서 위반·중복 422, 미배정 403 |
| `GET /api/submissions/{id}/final-confirmations` | 최종 확인 목록 | 200 |
| `PUT /api/submissions/{id}/confirmation` | **확정** — 요구 분야의 최종 확인이 모두 끝난 뒤, 마지막 순서 분야의 배정 담당자(기본 서류는 SECURITY). 신청 상태 → `CONFIRMED`. 방화벽 규칙을 만드는 서류면 규칙이 생성된다 | 200, 미확인 분야 있음 422, 권한 403 |
| `POST /api/security-requests/{id}/generated-documents` | **서류 생성** — body 없으면 현재 답변으로 초안, `submissionId` 를 주면 제출본/확정본, `aiSupplement:true` 면 AI 보완 서술(미확정)을 덧붙인다. 값마다 출처·확인 상태를 싣고 미확정은 "담당자 확인 필요" | 201 |
| `POST /api/security-requests/{id}/draft-generations` | AI 보완 초안 생성 별칭(= `generated-documents` + `aiSupplement:true`) | 201 |
| `GET /api/security-requests/{id}/generated-documents` | 서류 버전 목록 | 200 |
| `GET /api/generated-documents/{id}` | 서류 하나(섹션 + Markdown) | 200 |
| `GET /api/generated-documents/{id}/download` | **다운로드** `format=MARKDOWN`(기본)·`PDF`. 허용 형식은 게시본 `docTypes[].exportFormats` 가 정한다. 다운로드마다 감사 이력 | 200, 허용 안 된 형식 422, 미구현 형식(HWPX) 503, 모르는 형식 400 |
| `GET /api/security-requests/{id}/documents` | 서류 데이터(JSON) — 현재 답변 또는 `submissionId` 제출본으로 렌더링한 섹션·값·출처. 저장하지 않는다 | 200 |
| `GET /api/document-export-formats` | 출력 형식 목록과 구현 여부(`implemented`) | 200 |

- 부족하면 4→3 차수 반환으로 돌려보내고, 다시 제출하면 새 개정본(`sequenceNo` 증가)이 된다. 이전 제출본은 불변이다.
- 서류 확정 ≠ 방화벽 적용. 확정은 규칙을 "적용 대기"로 만들 뿐이고 적용 기록은 INFRA 가 따로 남긴다.

### 방화벽 규칙 API

| 요청 | 설명 | 응답 |
|---|---|---|
| `GET /api/security-requests/{id}/firewall-rules` | 신청의 방화벽 규칙. 방화벽 서류 확정 때 제출본에서 자동 생성된다(상시 규칙의 재검토일 = 시작일 + 12개월) | 200 |
| `GET /api/firewall-rules` | 규칙 목록(조회 권한 범위). `effectiveStatus` 는 오늘 기준 계산값 — 적용된 규칙의 종료일 30일 안이면 EXPIRING, 지나면 EXPIRED | 200 |
| `PUT /api/firewall-rules/{id}/application` | INFRA 가 적용 완료 또는 적용 불가(사유 필수)를 기록. 시스템이 장비에 적용하지 않는다 | 200, INFRA 아님 403, 상태 위반 422 |
| `PUT /api/firewall-rules/{id}/revocation` | INFRA 가 회수(사유 필수)를 기록 | 200, 위와 같음 |
| `POST /api/firewall-rules/{id}/renewal-requests` | **규칙 연장**(신청자) `{newPeriod, note?}` — 규칙은 그대로 두고, 규칙 값·원 신청 답변을 미리 채운 새 방화벽 신청을 같은 사업 건에 만든다 | 201, 적용 완료 규칙이 아님·진행 중 연장 있음 422 |

### 배정·기한·알림·처리할 일·감사·지표 API

| 요청 | 설명 | 응답 |
|---|---|---|
| `GET /api/security-requests/{id}/assignments` | 배정 이력(활성·비활성, 재배정 필요 사유 `reassignmentReason` 포함) | 200 |
| `POST /api/security-requests/{id}/assignments` | **재배정**(관리자, 사유 필수, 등록된 활성 담당자만). 이전 배정은 비활성 이력으로 남는다 | 201, 관리자 아님 403, 종료된 신청·규칙 위반 422 |
| `GET /api/assignments/mine` | 내게 배정된 활성 배정 | 200 |
| `GET /api/reviewer-pools` | 분야별 담당자 목록(관리자, `field`, 비활성 포함, 지정 가능 여부 `eligible`·`unavailableReason`) | 200, 관리자 아님 403 |
| `POST /api/reviewer-pools` | 담당자 등록(`field`·`memberUsername`·`defaultReviewer`). 계정이 활성이고 분야 역할이어야 한다 | 201, 역할 불일치·중복 422 |
| `PUT /api/reviewer-pools/{id}/default` | 기본 담당자 지정(분야당 활성 기본 1명, 이전 기본은 해제). 멱등 | 200, 부적격 422 |
| `PUT /api/reviewer-pools/{id}/activation` | 등록 활성/비활성(`active`·`reason`). 배정은 바꾸지 않고 재배정이 필요한 신청 ID 를 돌려준다 | 200 |
| `GET /api/security-requests/{id}/deadlines` | 기한 목록. `status`(ON_TRACK·IMMINENT·OVERDUE·ENDED)는 오늘(Asia/Seoul) 기준 계산값. 보완 대기 중 협의 기한은 정지(`paused`)되고 답변 시 남은 영업일로 다시 시작한다 | 200 |
| `GET /api/deadline-reminder-schedule` | 기한 알림 스케줄(관리자): cron·사용 여부·다음 실행 시각 | 200 |
| `PUT /api/deadline-reminder-schedule` | 스케줄 변경 `{cron, enabled}` — 저장 즉시 반영(재시작 불필요). 기본 매일 08:10 | 200, cron 형식 오류 422 |
| `POST /api/deadline-reminder-runs` | 기한 알림 수동 실행(관리자, `asOf`·`securityRequestId` 선택) — 1영업일 전·경과 알림을 보낸다. 기한·종류당 한 번만 | 201, 관리자 아님 403 |
| `GET /api/notifications` | **알림**(`unreadOnly`·`page`·`size`) — 현재 역할·배정으로 볼 수 있는 것만. 접근을 잃은 알림은 숨는다 | 200 |
| `GET /api/notifications/unread-count` | 안 읽은 알림 수 | 200 |
| `PATCH /api/notifications/{id}` | 읽음 처리 `{read:true}`. 반복 호출해도 읽음 시각 유지 | 200, 남의 알림 404 |
| `GET /api/notification-channels` | 외부 알림 채널 상태(관리자): EMAIL(SMTP)·WEBHOOK. 환경변수가 비어 있으면 꺼짐 | 200 |
| `GET /api/todos` | **처리할 일** 목록(`page`·`size`) — 현재 역할·배정으로 지금 처리할 수 있는 업무. 알림 읽음과 무관하다 | 200 |
| `GET /api/todo-summary` | 처리할 일 건수(역할별 집계 + `total`, 관리자는 재배정 필요 신청) | 200 |
| `GET /api/security-requests/{id}/audit-events` | 신청의 처리 이력(조회 권한 범위) | 200 |
| `GET /api/audit-events` | **감사 이력 페이지** — 관리자는 전체, 담당자는 활성 배정된 신청과 그 사업 건, 신청자는 자기 업무 이력(로그인·다운로드·AI 호출 등 보안 메타 제외). `targetType`·`targetId`·`action`·`query`·`throughId`·`page`·`size`. 첫 응답의 `throughId` 를 다음 페이지에 넘기면 그 뒤 추가된 기록이 섞이지 않는다 | 200 |
| `GET /api/insights/summary` | **업무 지표**(관리자 전용): 유형·단계·상태별 신청 수, 단계별 평균 소요 영업일, 기한 상태, 보완·반환 비율, 1회 통과율, AI 제안 확인율, 규칙 상태. 조회할 때 계산하고 저장하지 않는다 | 200, 관리자 아님 403 |

- 모든 응답에 `X-Request-Id` 헤더가 붙고, 오류 ProblemDetail 과 감사 이력의 `requestId` 가 같은 값이다.
- 감사 이력은 업무 변경과 같은 트랜잭션에서 기록되며 `audit_events` 는 DB 트리거로 UPDATE·DELETE 를 거부한다. 값·비밀값·원문은 기록하지 않고 항목 키·출처·참조 ID 만 남긴다.
- 알림은 업무 커밋 뒤 별도 트랜잭션으로 저장한다. 알림 실패는 업무를 실패시키지 않는다.

### 관리자 설정 API — 질문·기한, 지식, AI 모델

세 가지 모두 관리자(ADMIN) 전용이다. 질문·기한 설정과 지식은 **초안 → 비교 → 게시**이고 사업 건은 만들 때의 게시본에 고정된다. AI 모델 구성은 저장이 곧 적용이며 다음 AI 호출부터 쓰인다.

| 요청 | 설명 | 응답 |
|---|---|---|
| `GET /api/configurations/current` | 질문·기한 설정 초안 | 200 |
| `PUT /api/configurations/{id}` | 초안 저장(검증: 질문 최대 200개, 기한 1~365 영업일, 기본 질문 계약 보호). 저장만으로는 업무에 반영되지 않는다 | 200, 검증 400/422, 충돌 409 |
| `GET /api/configurations/{id}/comparison` | 저장된 초안과 현재 게시본의 차이 | 200 |
| `POST /api/configurations/{id}/publications` | **게시** — 새 버전을 만든다. 기존 사업 건과 거기에 추가하는 신청은 예전 버전 유지 | 201, 내용 같음 422, 비교 뒤 변경됨 409 |
| `GET /api/publications` | 게시 이력(`page`·`size`): 게시자·메모·직전 버전 | 200 |
| `GET /api/publications/{id}/comparison` | 게시 당시의 변경 내역 | 200 |
| `GET /api/knowledge-drafts/current` | 지식 초안 상태(`revision`, 기준 버전, 조각·기관 정보 수) | 200 |
| `PUT /api/knowledge-drafts/current` | 초안 메모 저장 `{expectedRevision, note}` | 200, 409 |
| `GET /api/knowledge-drafts/current/snippets` | 초안의 공개 지식 조각(`q`·`page`·`size`) | 200 |
| `GET /api/knowledge-drafts/current/snippets/{id}` | 초안의 조각 하나 | 200 |
| `POST /api/knowledge-snippets` | 조각을 **초안에** 등록. `fieldKeys` 는 현재 설정 게시본의 실제 질문 키 1개 이상, `docTypes` 는 실제 서류 유형 | 201, 검증 422 |
| `PUT /api/knowledge-snippets/{id}` | 초안의 조각 수정·사용 중지(`active:false`, 삭제는 없음) | 200, 409 |
| `GET /api/knowledge-drafts/current/private-contexts` | 초안의 기관 비공개 정보(`q`·`page`·`size`). 조회는 접근 감사를 남긴다 | 200 |
| `GET /api/knowledge-drafts/current/private-contexts/{id}` | 초안의 기관 정보 하나 | 200 |
| `POST /api/private-contexts` | 기관 비공개 정보를 초안에 등록(종류 SERVER·NETWORK_ZONE·ASSET·NOTE, 이름·별칭·속성). `visibleRoles` 에 APPLICANT 불가. 시연에서는 합성 데이터만 | 201, 검증 422 |
| `PUT /api/private-contexts/{id}` | 초안의 기관 정보 수정·사용 중지 | 200, 409 |
| `GET /api/knowledge-drafts/current/comparison` | 초안과 현재 지식 게시본의 차이 | 200 |
| `POST /api/knowledge-drafts/current/publications` | **지식 게시** `{expectedRevision}` — 새 지식 게시본. 이후 만드는 사업 건부터 적용 | 201, 내용 같음 422, 409 |
| `GET /api/knowledge-publications` | 지식 게시 이력(`page`·`size`) | 200 |
| `GET /api/knowledge-publications/{id}` | 지식 게시본 전체 사본(비공개 원문 포함) | 200 |
| `GET /api/knowledge-publications/{id}/comparison` | 그 게시 당시의 변경 내역 | 200 |
| `GET /api/knowledge-snippets` | **게시된** 활성 공개 지식 목록(로그인한 사용자) | 200 |
| `GET /api/knowledge-snippets/search` | 게시된 공개 지식 전문검색(`q`·`limit`·`knowledgePublicationId`) | 200 |
| `GET /api/private-contexts` | 기관 정보 목록(관리자) | 200 |
| `GET /api/private-contexts/suggestions` | 담당자 입력 자동완성 — 호출자 역할에 보이는 행만(신청자는 항상 빈 목록). `q`·`knowledgePublicationId` | 200 |
| `GET /api/llm-configurations` | 모델 구성 목록(키 값 없음, `keyConfigured` 만) | 200 |
| `GET /api/llm-configurations/management` | 관리 화면용 — 업무 모드(fake/live), 허용 호스트, 허용된 키 환경변수 이름과 설정 여부, 용도별로 실제 선택되는 구성 | 200 |
| `GET /api/llm-configurations/{id}` | 구성 하나 | 200 |
| `POST /api/llm-configurations` | 구성 추가 — 이름·모델·주소·키 환경변수 이름·용도·온도·최대 토큰·시간 제한·우선순위. 주소는 `LLM_ALLOWED_HOSTS`, 키 이름은 `LLM_ALLOWED_KEY_ENVS` 안에서만. 최초 이력 기록 | 201, 검증 422 |
| `PUT /api/llm-configurations/{id}` | 구성 변경(`expectedRevision`) — 저장하면 다음 AI 호출부터 적용, 이력 기록 | 200, `409 REVISION_CONFLICT` |
| `POST /api/llm-configurations/comparisons` | 새 구성을 저장하지 않고 검증·비교 | 200 |
| `POST /api/llm-configurations/{id}/comparisons` | 변경 전후를 저장하지 않고 비교 | 200 |
| `GET /api/llm-configurations/{id}/history` | 구성별 변경 이력(`page`·`size`, 수정·삭제 불가) | 200 |
| `POST /api/llm-configurations/{id}/restorations` | 과거 값을 지금 정책으로 다시 검증해 새 변경으로 복원(사유 필수) | 200 |
| `POST /api/llm-connection-tests` | **연결 시험** — 고정 합성 문장으로 실제 모델을 호출한다(최대 512토큰·30초·재시도 없음). 설정은 바꾸지 않는다. `201` 은 연결 성공이 아니며 응답의 `status`·`outcome` 을 본다 | 201 |
| `GET /api/llm-connection-tests/{id}` | 시험 결과 하나 | 200 |

- `LLM_MODE=fake` 면 업무 AI 호출은 네트워크 없이 고정 응답으로 동작한다(테스트·오프라인 시연). 연결 시험은 이 모드와 관계없이 실제 모델을 부른다.
- seed 구성 3건(우선순위 순): `openrouter-deepseek-v4.1-flash-1017`(`OPENROUTER_API_KEY`), `openai-default`(`OPENAI_API_KEY`), `ollama-exaone`(`OLLAMA_API_KEY`). 용도에 맞는 활성 구성 중 키 환경변수가 설정된 첫 구성이 쓰인다.
- 추론형 모델은 추론 토큰이 최대 토큰을 함께 쓴다. 응답이 비거나 잘리면 그 구성의 최대 토큰을 올린다.
- 지식 조각 데이터와 일괄 등록 방법은 [docs/knowledge/README.md](docs/knowledge/README.md).

## 현재 구현 범위

컨텍스트(패키지)별이다. 구조와 흐름은 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 2·3장.

- `auth`: 회원가입·세션 로그인·실패 잠금·CSRF, 역할 4종, 관리자의 역할 부여·계정 활성화, 계정 변경 시 기존 세션 회수, 시연 계정 5건
- `request`: 사업 건·신청·답변(항목별 권한·형식·미해당), 권한별 신청 검색, 문서 업로드, 서버·통신 구성, 협의 기준본과 변경 기록, 최종 서술, 제출(멱등성 키·불변 스냅샷)·문서함, 처리 기한과 기한 알림 스케줄
- `configuration`: 질문·기한 설정 초안 편집·비교·게시와 게시 이력. 서류 유형·질문·기한·공휴일·출력 형식이 게시본 데이터다
- `review`: 협의 요청, 담당자 입력·확인, 분야 협의 완료(INFRA → SECURITY), 보완 요청·답변·해소, 차수 반환, 종결, 최종 확인·확정, 요청함, 처리할 일
- `assignment`: 분야별 담당자 등록·기본 담당자·자동 배정·재배정, 담당자 변경 시 재배정 필요 판정
- `audit`: 추가 전용 감사 이력 기록과 권한별 페이지 조회
- `knowledge`: 공개 지식 조각(전문검색)과 기관 비공개 정보, 초안·게시본, 항목 도움말
- `llm`: 공통 게이트웨이(Spring AI 2.0, OpenAI 호환, DB 구성으로 런타임 생성)와 fake 모드, 모델 구성 관리·이력·복원·연결 시험, 신청자 인터뷰, AI 항목 제안, 서류 유형 제안, 문서 요약(PDF·DOCX·HWPX·HWP·TXT 추출), 담당자 질의, 협의 결과 요약, 서버 구성 제안
- `document`: 서류 초안·제출본·확정본 렌더링과 다운로드(Markdown·PDF), AI 최종 서술 생성
- `policy`: 방화벽 규칙 생성·적용 기록·회수·연장·만료 계산
- `notification`: 화면 안 알림(현재 수신 권한 기준) + 외부 채널(SMTP·웹훅, 환경변수가 없으면 꺼짐)
- `insight`: 업무 지표(관리자, 조회 시 계산)
- `installation`: 운영 기관 설정값
- `common`: ProblemDetail 오류 처리, Spring Security 설정, 게시본 스키마 값 객체, 영업일 달력

없는 것: HWPX 출력, 양식(템플릿) 편집, 서류의 외부 전달, SSO, 절차 단계·순서의 데이터화, 벡터 검색. 실제 SMTP·웹훅 발송과 실제 한글(HWP) 파일 추출은 합성 자료로만 확인했다. 자세한 목록은 ARCHITECTURE 9장.

시연 계정·비밀번호는 합성 데이터이며 운영 값이 아니다. 비밀값은 환경변수로만 받는다.

## DB 명세

명세와 의도적으로 다른 결정은 `docs/ARCHITECTURE.md` 5장, 기술 결정은 4장에 있다.

스키마는 Flyway 마이그레이션(`src/main/resources/db/migration/`)으로만 바꾼다. JPA는 `ddl-auto: validate`로 엔티티와 테이블이 맞는지만 확인한다. main에 머지된 마이그레이션 파일은 수정하지 않고 새 파일(`V<연월일시분>__<설명>.sql`)을 추가한다. **외래 키 제약(FOREIGN KEY)은 두지 않는다**(팀 결정 2026-11-07, 성능·운영 단순화). 참조 무결성은 도메인·서비스가 지키고, ERD의 관계선은 논리적 참조를 뜻한다. 참조 컬럼에는 조회용 인덱스를 둔다. 업무 규칙을 담은 CHECK·UNIQUE(단계×상태 조합, 사업 건당 서류 유형별 진행 중 신청 1건(종료 상태 제외), 답변 유일, 멱등성 키 등)는 유지한다. 테이블·컬럼은 snake_case, 테이블명은 복수형이다. **기본 키 컬럼 이름은 맨 `id`를 쓰지 않고 `<테이블 단수형>_id`로 짓는다**(예: `business_cases.business_case_id`, `reviews.review_id`). 이렇게 하면 다른 테이블의 외래 키(`security_requests.business_case_id`)와 이름·형식이 같아지며, **같은 이름의 컬럼은 모든 테이블에서 같은 의미·같은 데이터 형식**이어야 한다(예: `business_case_id`는 어디서나 BIGINT, `created_by`는 어디서나 VARCHAR(50) username). `members`의 기본 키는 로그인 아이디 `username`(VARCHAR(50))이고, 이를 참조하는 컬럼도 같은 형식에 `_username` 접미사를 쓴다(`applicant_username`, `created_by`는 예외로 유지). 운영 기관은 설치당 1곳 고정이라 기관 테이블과 기관 키 컬럼은 없다(FUNCTIONAL_SPEC.md 3-4장).

### ERD (마이그레이션 33건, 테이블 35개)

테이블과 컬럼은 마이그레이션 파일을 읽어 만든 것이다(2026-11-11). 관계선은 외래 키 제약이 아니라 **참조 컬럼의 의미**를 그린 것이다 — DB 에는 FK 가 없다. `procedures` 는 초기 마이그레이션에만 있고 지금 쓰는 코드가 없다.

```mermaid
erDiagram
    members ||--o{ business_cases : "applicant_username"
    members ||--o{ submissions : "submitted_by"
    publications ||--o{ business_cases : "생성 시점 게시본 고정"
    publications ||--o{ submissions : "제출 시점 설정 버전"
    business_cases ||--|{ security_requests : "1 : N, 진행 중 신청은 유형당 1건"
    business_cases ||--o{ answers : "scope=CASE (security_request_id NULL)"
    security_requests ||--o{ answers : "scope=REQUEST"
    security_requests ||--o{ submissions : "sequence_no 증가"
    security_requests ||--o{ final_drafts : "최종 서술 생성·확인 이력"
    submissions ||--o{ final_confirmations : "분야별 1건, INFRA 선행"
    submissions ||--o{ firewall_rules : "FIREWALL 확정 시 생성"
    security_requests ||--o{ inquiries : "담당자별 대화"
    inquiries ||--o{ inquiry_messages : ""
    security_requests ||--o{ generated_documents : "초안/제출본/확정본"
    business_cases ||--o{ uploaded_files : ""
    uploaded_files ||--o{ analysis_jobs : "AI 요약"
    members ||--o{ notifications : "수신자"
    publications ||--o{ configurations : "초안의 기준 버전"
    security_requests ||--o{ supplements : "보완 요청"
    reviews ||--o{ supplements : "요청한 분야 협의"
    security_requests ||--o{ stage_returns : "차수 반환 이력"
    security_requests ||--o{ reviews : "분야별 1건"
    security_requests ||--o{ assignments : "분야당 활성 1건"
    security_requests ||--o{ deadlines : "저장형 기한"
    members ||--o{ reviewer_pools : "분야별 담당자"
    knowledge_publications ||--o{ business_cases : "생성 시점 지식 게시본 고정"
    business_cases ||--o{ interview_turns : "신청자 인터뷰"
    security_requests ||--o| infrastructure_plans : "서버·통신 구성(신청당 1행)"
    security_requests ||--o{ consultation_records : "협의 기준본·변경 기록"
    llm_configurations ||--o{ llm_configuration_histories : "변경 이력(수정·삭제 불가)"
    llm_configurations ||--o{ llm_connection_tests : "연결 시험"

    members {
        varchar(50)  username PK "로그인 아이디"
        varchar(100) password_hash "bcrypt 해시"
        varchar(100) display_name
        varchar(100) department "선택"
        varchar(20)  role "APPLICANT | SECURITY | INFRA | ADMIN"
        varchar(10)  signup_source "SEED | SELF"
        boolean      active "false면 로그인 불가"
        integer      failed_login_count "연속 실패 횟수"
        timestamptz  locked_until "잠금 해제 시각"
        timestamptz  created_at
        timestamptz  updated_at
        varchar(200) email "메일 알림 수신 주소(선택). 본인 또는 기관 관리자가 설정"
        bigint       session_version "역할·활성 변경 시 증가 — 기존 세션 회수"
    }
    publications {
        bigint       publication_id PK
        integer      version
        jsonb        field_schema "docTypes(유형 목록) + fields(질문 항목)"
        timestamptz  published_at
        timestamptz  created_at
        timestamptz  updated_at
        varchar(50)  published_by
        varchar(500) note
        integer      previous_version
    }
    business_cases {
        bigint       business_case_id PK
        varchar(50)  applicant_username
        varchar(200) title
        text         summary
        varchar(100) department
        bigint       publication_id
        bigint       revision "낙관적 잠금"
        varchar(50)  created_by
        varchar(50)  updated_by
        timestamptz  created_at
        timestamptz  updated_at
        bigint       knowledge_publication_id "생성 시점 지식 게시본. 기존 사업과 추가 신청은 같은 버전을 유지한다"
    }
    security_requests {
        bigint      security_request_id PK
        bigint      business_case_id
        varchar(30) doc_type "게시본 docTypes 코드(CHECK 없음)"
        smallint    stage "1~4"
        varchar(30) status "단계x상태 CHECK"
        bigint      revision
        varchar(50) created_by
        varchar(50) updated_by
        timestamptz created_at
        timestamptz updated_at
        bigint      renews_firewall_rule_id "이 신청이 연장하려는 방화벽 규칙(연장 신청일 때만)"
    }
    answers {
        bigint       answer_id PK
        varchar(10)  scope "CASE | REQUEST"
        bigint       business_case_id
        bigint       security_request_id
        varchar(100) field_key "게시본 저장 키"
        text         value
        varchar(20)  value_source "NULL = 담당자 확인 필요"
        text         evidence "AI 근거"
        boolean      confirmed
        varchar(50)  confirmed_by
        timestamptz  confirmed_at
        boolean      needs_review "재검토 표시"
        bigint       revision
        varchar(50)  created_by
        varchar(50)  updated_by
        timestamptz  created_at
        timestamptz  updated_at
        boolean      not_applicable "선택 항목의 미해당"
    }
    reviews {
        bigint      review_id PK
        bigint      security_request_id
        varchar(20) field "INFRA | SECURITY"
        varchar(30) status "WAITING…COMPLETED, RECHECK"
        jsonb       checklist
        text        opinion
        varchar(50) allocated_by "INFRA 할당 완료자"
        timestamptz allocated_at
        varchar(50) completed_by
        timestamptz completed_at
        bigint      revision
        varchar(50) created_by
        varchar(50) updated_by
        timestamptz created_at
        timestamptz updated_at
    }
    supplements {
        bigint      supplement_id PK
        bigint      security_request_id
        bigint      review_id
        varchar(20) field
        jsonb       target_field_keys "보완 대상 항목 키"
        text        reason "필수"
        varchar(20) status "OPEN | ANSWERED | RESOLVED"
        varchar(50) requested_by
        timestamptz requested_at
        text        answer_note
        timestamptz answered_at
        varchar(50) resolved_by
        timestamptz resolved_at
        bigint      revision
        timestamptz created_at
        timestamptz updated_at
    }
    final_drafts {
        bigint       final_draft_id PK
        bigint       security_request_id
        varchar(64)  source_fingerprint
        text         original_text "AI 원문 또는 최초 작성문"
        text         text "사용자 확인 본문"
        varchar(30)  value_source "AI_INFERRED | USER_ANSWER"
        varchar(200) model_reference
        varchar(50)  confirmed_by
        timestamptz  confirmed_at
        boolean      invalidated
        bigint       revision
        timestamptz  created_at
        timestamptz  updated_at
    }
    final_confirmations {
        bigint      final_confirmation_id PK
        bigint      submission_id
        bigint      security_request_id
        varchar(20) field "INFRA | SECURITY, UNIQUE(submission_id, field)"
        text        opinion
        varchar(50) confirmed_by
        timestamptz confirmed_at
        timestamptz created_at
        timestamptz updated_at
    }
    knowledge_snippets {
        bigint       knowledge_snippet_id PK
        varchar(200) source_title
        varchar(50)  source_version
        varchar(200) source_locator "근거 표시"
        jsonb        field_keys "적용 항목 키(1차 필터)"
        jsonb        doc_types
        text         body
        boolean      active
        bigint       revision
        timestamptz  created_at
        timestamptz  updated_at
        tsvector     search_vector "GENERATED, GIN"
    }
    private_contexts {
        bigint       private_context_id PK
        varchar(30)  kind "SERVER | NETWORK_ZONE | ASSET | NOTE"
        varchar(200) name
        jsonb        aliases
        jsonb        attributes "합성 값만"
        jsonb        visible_roles "APPLICANT 금지"
        text         note
        boolean      active
        bigint       revision
        timestamptz  created_at
        timestamptz  updated_at
    }
    llm_configurations {
        bigint       llm_configuration_id PK
        varchar(100) name
        varchar(30)  provider "OPENAI_COMPATIBLE"
        varchar(300) base_url "허용 목록 호스트"
        varchar(100) model
        varchar(100) api_key_env "환경변수 이름만"
        jsonb        purposes
        numeric(3)   temperature
        integer      max_tokens
        integer      timeout_seconds
        integer      priority
        boolean      active
        bigint       revision
        timestamptz  created_at
        timestamptz  updated_at
    }
    inquiries {
        bigint       inquiry_id PK
        bigint       security_request_id
        varchar(50)  staff_username
        varchar(20)  field
        varchar(200) title
        timestamptz  created_at
        timestamptz  updated_at
    }
    inquiry_messages {
        bigint       inquiry_message_id PK
        bigint       inquiry_id
        varchar(20)  role "USER | ASSISTANT"
        text         text
        jsonb        evidence_refs "S·P 태그"
        varchar(200) model_reference
        boolean      flagged "근거 없는 내부 정보가 감지되어 가려진 답변"
        timestamptz  created_at
        timestamptz  updated_at
    }
    generated_documents {
        bigint       generated_document_id PK
        bigint       security_request_id
        bigint       submission_id "DRAFT 면 NULL"
        varchar(20)  kind "DRAFT | SUBMITTED | CONFIRMED"
        integer      version
        varchar(200) title
        jsonb        sections
        text         rendered_markdown
        text         ai_supplement "미확정"
        varchar(200) model_reference
        integer      unconfirmed_count
        varchar(50)  generated_by
        timestamptz  generated_at
        timestamptz  created_at
        timestamptz  updated_at
    }
    firewall_rules {
        bigint       firewall_rule_id PK
        bigint       security_request_id
        bigint       submission_id
        integer      rule_no
        varchar(200) source
        varchar(200) destination
        varchar(20)  protocol
        varchar(50)  port
        date         start_date
        date         end_date
        date         review_date "end 또는 review 필수"
        varchar(20)  status "PENDING_APPLY | APPLIED | NOT_APPLICABLE | REVOKED"
        varchar(50)  applied_by
        timestamptz  applied_at
        text         application_note
        text         reason
        bigint       revision
        timestamptz  created_at
        timestamptz  updated_at
        bigint       renewal_request_id "이 규칙을 연장하려고 만든 신청(있으면). 규칙 값은 바뀌지 않는다"
    }
    uploaded_files {
        bigint       uploaded_file_id PK
        bigint       business_case_id
        varchar(255) original_name
        varchar(64)  stored_name
        varchar(100) content_type
        bigint       size_bytes
        varchar(50)  uploaded_by
        timestamptz  uploaded_at
        timestamptz  created_at
        timestamptz  updated_at
    }
    analysis_jobs {
        bigint       analysis_job_id PK
        bigint       business_case_id
        bigint       uploaded_file_id
        varchar(20)  status "QUEUED | RUNNING | NEEDS_REVIEW | FAILED"
        text         summary "미확정"
        varchar(200) model_reference
        varchar(500) error_message
        varchar(50)  requested_by
        timestamptz  started_at
        timestamptz  finished_at
        bigint       revision
        timestamptz  created_at
        timestamptz  updated_at
    }
    notifications {
        bigint       notification_id PK
        varchar(50)  recipient_username
        varchar(200) event_key "UNIQUE(수신자, 사건)"
        varchar(40)  kind
        varchar(200) title
        varchar(300) link
        timestamptz  read_at
        timestamptz  created_at
        timestamptz  updated_at
        bigint       security_request_id "대상 신청"
        varchar(20)  recipient_role "발송 당시 수신 역할"
    }
    configurations {
        bigint       configuration_id PK
        jsonb        draft_field_schema
        integer      based_on_version
        varchar(500) note
        varchar(50)  updated_by
        bigint       revision
        timestamptz  created_at
        timestamptz  updated_at
    }
    reminder_schedules {
        bigint       reminder_schedule_id PK
        varchar(30)  name "DEADLINE, UNIQUE"
        varchar(100) cron_expression "Spring 6필드, Asia/Seoul"
        boolean      enabled
        varchar(50)  updated_by
        timestamptz  created_at
        timestamptz  updated_at
    }
    stage_returns {
        bigint      stage_return_id PK
        bigint      security_request_id
        smallint    from_stage "2~4"
        smallint    to_stage "1~3, 허용 조합 CHECK"
        text        reason "필수"
        jsonb       target_field_keys "보완 필요 강조 항목"
        varchar(50) returned_by
        timestamptz returned_at
        timestamptz created_at
        timestamptz updated_at
        jsonb       field_reasons "항목 키→항목별 사유"
    }
    reviewer_pools {
        bigint      reviewer_pool_id PK
        varchar(20) field
        varchar(50) member_username
        boolean     is_default "분야당 활성 기본 1명"
        boolean     active
        timestamptz created_at
        timestamptz updated_at
    }
    assignments {
        bigint      assignment_id PK
        bigint      security_request_id
        varchar(20) field
        varchar(50) assignee_username
        boolean     active "분야당 활성 1건(부분 유일)"
        text        reason "재배정 사유"
        varchar(50) assigned_by "NULL = 자동"
        timestamptz assigned_at
        timestamptz deactivated_at
        timestamptz created_at
        timestamptz updated_at
    }
    deadlines {
        bigint      deadline_id PK
        bigint      security_request_id
        varchar(20) field "REVIEW 만"
        varchar(30) kind "REVIEW | SUPPLEMENT_RESPONSE | FINAL_CONFIRMATION"
        timestamptz started_at
        date        due_date "저장형, 수정 없음"
        integer     policy_version
        boolean     active
        timestamptz ended_at
        timestamptz created_at
        timestamptz updated_at
        boolean     paused "보완 대기로 정지되어 종료된 기한(재개 시 false 로 바뀌고 새 행이 생긴다)"
        integer     remaining_business_days "정지 시점에 남아 있던 영업일(재개 기한 계산용)"
    }
    audit_events {
        bigint       audit_event_id PK "추가 전용(트리거)"
        timestamptz  occurred_at
        varchar(50)  actor_username
        varchar(20)  actor_role
        varchar(50)  target_type
        varchar(100) target_id
        bigint       target_revision
        varchar(50)  action
        text         reason
        text         summary "값·원문 없음"
        varchar(64)  request_id
    }
    submissions {
        bigint       submission_id PK
        bigint       security_request_id
        bigint       publication_id
        integer      sequence_no "신청 내 개정본 번호"
        jsonb        snapshot "불변 스냅샷"
        varchar(100) idempotency_key
        varchar(50)  submitted_by
        timestamptz  submitted_at
        timestamptz  created_at
        timestamptz  updated_at
    }
    procedures {
        bigint       procedure_id PK
        varchar(50)  code "절차 고정 코드. API·화면에서 id 대신 사용"
        varchar(100) name
        varchar(500) description
        boolean      active
        timestamptz  created_at
        timestamptz  updated_at
    }
    interview_turns {
        bigint       interview_turn_id PK
        bigint       business_case_id
        varchar(50)  applicant_username
        varchar(100) field_key
        varchar(20)  mode
        text         question
        text         reply
        text         proposed_value
        jsonb        evidence_refs
        varchar(64)  context_fingerprint
        bigint       based_revision
        bigint       answer_revision
        varchar(200) model_reference
        boolean      flagged
        boolean      applied
        bigint       revision
        timestamptz  created_at
        timestamptz  updated_at
    }
    knowledge_publications {
        bigint       knowledge_publication_id PK
        integer      version
        jsonb        snapshot "공개 지식 + 기관 정보 전체 사본"
        timestamptz  published_at
        varchar(50)  published_by
        varchar(500) note
        integer      previous_version
        timestamptz  created_at
        timestamptz  updated_at
    }
    knowledge_drafts {
        bigint       knowledge_draft_id PK
        integer      based_on_version
        varchar(500) note
        bigint       revision
        bigint       change_sequence
        varchar(50)  updated_by
        timestamptz  created_at
        timestamptz  updated_at
    }
    llm_configuration_histories {
        bigint       llm_configuration_history_id PK
        bigint       llm_configuration_id
        bigint       configuration_revision
        jsonb        snapshot "그 시점 구성 전체 값"
        varchar(20)  action
        varchar(50)  changed_by
        varchar(500) reason
        bigint       restored_from_id
        timestamptz  created_at
        timestamptz  updated_at
    }
    llm_connection_tests {
        bigint      llm_connection_test_id PK
        bigint      llm_configuration_id
        bigint      configuration_revision
        jsonb       snapshot
        varchar(40) outcome "성공·키 미설정·인증 실패·시간 초과 등"
        bigint      elapsed_millis
        varchar(50) tested_by
        timestamptz created_at
        timestamptz updated_at
    }
    infrastructure_plans {
        bigint      security_request_id PK
        bigint      revision
        text        content "서버·통신 구간·운영 계획 JSON(schemaVersion 2)"
        varchar(50) updated_by
        timestamptz updated_at
    }
    consultation_records {
        bigint       consultation_record_id PK
        bigint       security_request_id
        varchar(30)  kind "기준본 / 변경 기록 구분"
        varchar(200) actor_name
        varchar(30)  actor_role
        timestamptz  created_at
        text         payload "업무 원문 포함 — 감사 이력과 분리"
    }
```

### 공통 컬럼

대부분의 테이블은 `BaseTimeEntity` 의 `created_at`·`updated_at`(TIMESTAMPTZ, JPA Auditing 이 자동 기록)을 가진다. 담당자를 남겨야 하는 업무 테이블은 `BaseAuditEntity` 로 `created_by`·`updated_by`(VARCHAR(50), 로그인 username)를 더한다. 변경 가능한 애그리거트는 낙관적 잠금용 `revision` 을 가진다.
