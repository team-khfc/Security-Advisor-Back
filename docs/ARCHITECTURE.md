# Security-Advisor-Back 구조 안내 (사람·AI 에이전트 공용)

보안길잡이(SecurityAdvisor) 백엔드를 처음 받은 사람이나 AI 가 **코드를 이어서 개발할 수 있도록** 쓴 문서다. 규칙의 원본은
팀 규칙 문서(AGENTS.md·FUNCTIONAL_SPEC.md 등 — 저장소 루트에 사본이 있으면 그것, 없으면 Claude 프로젝트 "보안길잡이")이고,
이 문서는 **"지금 코드가 어떻게 생겼는가"** 만 다룬다. 결정의 이유는 `docs/adr/`, 규칙과 다르게 한 것은 `docs/DEVIATIONS.md`,
API 표·ERD 는 `README.md`, API 계약은 `docs/api/openapi.yaml`(생성물).

마지막 갱신: 2026-11-08 (커밋 e18e57d 기준).

## 1. 한 장 요약

- Spring Boot 4.1 / Java 25 / Gradle(Kotlin DSL) / PostgreSQL 17 + Flyway / Spring Data JPA / Spring AI 2.0(OpenAI 호환 어댑터 1종) / PDFBox.
- 단일 모듈, 패키지 = 바운디드 컨텍스트: `kr.securityadvisor.<컨텍스트>.{controller,dto,service,domain,repository}`.
- 운영 기관은 설치당 1곳(기관 테이블 없음, `.env`). 역할 4종(APPLICANT·SECURITY·INFRA·ADMIN)은 세션의 `MemberPrincipal` 에 있고 권한 판단은 서비스 계층이 한다.
- 업무 절차: 1 작성 → 2 담당자 협의(INFRA 선행 → SECURITY) → 3 사용자 최종 검토 → 4 제출·최종 확인·확정. 상태는 `SecurityRequest`(단계×상태)가 갖고, 전이는 도메인 메서드로만.
- 서류 유형·질문·기한 정책·공휴일·출력 형식은 **게시본(Publication) 데이터**다. 코드는 "행위 속성"(협의 분야, 규칙 생성 여부)으로만 분기한다(ADR 0005).
- 감사 이력은 추가 전용이며 업무 변경과 같은 트랜잭션. 알림·조회 감사·AI 호출 감사는 별도 트랜잭션(REQUIRES_NEW)이라 실패해도 업무를 막지 않는다.

## 2. 컨텍스트 지도

| 컨텍스트 | 맡는 것 | 애그리거트(테이블) | 들어오는 의존(이 컨텍스트를 쓰는 곳) |
|---|---|---|---|
| `common` | 오류 모델(ProblemDetail), 보안 설정·세션·CSRF, 요청 ID, 게시본 스키마 값 객체(`FieldSchema`·`DocTypeDefinition`·`FieldDefinition`·`DocTypeCode`·`ExportFormat`·`ReviewField`·`ValueSource`), 영업일 달력, JSON 코덱 | — | 전부 |
| `installation` | 운영 기관 설정값(`GET /api/installation`) | — | document |
| `audit` | 감사 이력 기록(`AuditRecorder`)·조회 | audit_events | 거의 전부 |
| `auth` | 계정·로그인·잠금·회원가입·역할 부여, 다른 컨텍스트용 조회 창구 `MemberDirectory` | members | assignment, review |
| `configuration` | 설정 초안 편집·게시, 게시본 조회(`PublicationService`) | configurations, publications | request, review, knowledge, llm, document, policy, insight |
| `request` | 사업 건·신청·답변·최종 초안 확인·제출본·기한·업로드 파일·기한 알림 스케줄 | business_cases, security_requests, answers, final_drafts, submissions, deadlines, uploaded_files, reminder_schedules | review, llm, document, policy, insight |
| `assignment` | 분야별 담당자 목록·자동 배정·재배정 | reviewer_pools, assignments | request, review |
| `review` | 협의·보완·차수 반환·종결·최종 확인·확정·요청함·처리할 일 | reviews, supplements, stage_returns, final_confirmations | llm, policy, insight |
| `knowledge` | 공개 지식 스니펫(FTS)·비공개 컨텍스트·항목 도움말 | knowledge_snippets, private_contexts | llm, document |
| `llm` | LLM 구성, 공통 게이트웨이(감사 래퍼 + Spring AI/fake 백엔드), AI 제안, 담당자 질의, 문서 요약 작업 | llm_configurations, inquiries, inquiry_messages, analysis_jobs | document |
| `document` | 서류 렌더링(섹션·Markdown)·출력 형식 변환(Markdown·PDF) | generated_documents | — |
| `policy` | 방화벽 규칙 생성·적용 기록·회수·만료 계산 | firewall_rules | insight |
| `notification` | 화면 안 알림(`Notifier` 인터페이스) + 외부 채널(`NotificationChannel`: SMTP·웹훅, 비동기·실패 무시) | notifications | request, review |
| `insight` | 업무 지표(조회 시 계산) | — | — |

의존 방향(패키지 import 기준, 2026-11-08):

```
common ← 전부           audit ← 거의 전부         auth ← assignment, review
configuration ← request, review, knowledge, llm, document, policy, insight
request ← review, llm, document, policy, insight      assignment ← request, review
review ← llm, policy, insight      knowledge ← llm, document      llm ← document
notification ← request, review     installation ← document
```

순환은 ArchUnit(`architecture/ArchitectureTest.featuresHaveNoCycles`)이 막는다. request→review 는 금지이므로 request 가 review 의 사실이 필요하면
**포트**를 쓴다: `request.service.ReviewFactsProvider`(인터페이스) ← `review.service.ReviewFactsAdapter`(구현).

### 컨텍스트 간 연결 방식 (ADR 0003)

| 방식 | 예 |
|---|---|
| ID + 공개 서비스 호출 | review 가 `AssignmentService.autoAssign/activeAssignee`, `AnswerService.inputByStaff` 를 호출 |
| 포트/어댑터(역방향 의존 회피) | `ReviewFactsProvider` / `ReviewFactsAdapter` |
| 같은 트랜잭션의 동기 ApplicationEvent | `AnswerChangedEvent`(request→review: 완료된 협의 재검토·제출된 신청 담당자 알림), `RequestConfirmedEvent`(review→policy: 방화벽 규칙 생성) |
| 커밋 후 비동기 이벤트 | `DocumentAnalysisService.AnalysisQueued`(@Async + AFTER_COMMIT + TransactionTemplate) |
| 조회 전용 창구 | `auth.service.MemberDirectory`(엔티티를 밖으로 내보내지 않음), `review.service.RequiredReviewFields` |

## 3. 주요 흐름을 코드로 따라가기

| 흐름 | 진입 | 거치는 곳 |
|---|---|---|
| 사업 건 생성 | `BusinessCaseController` → `BusinessCaseService.create` | 현재 게시본 고정 → `BusinessCase.create` + 유형별 `SecurityRequest.create` + 공통 항목 자리 답변(사업명·개요·부서는 입력값으로, 담당자 항목은 "담당자 확인 필요" 자리로) → 감사 |
| 답변·AI 제안 | `SecurityRequestController`/`BusinessCaseController` → `AnswerService` / `AiProposalService` | `Answer` 도메인 메서드(출처 전이) → `AnswerChangedEvent` → review 재검토 |
| 협의 요청(1→2) | `ReviewController` → `ReviewService.requestReview` | `RequiredReviewFields` → `Review` 생성 → `AssignmentService.autoAssign` → `DeadlineService.startReviewDeadlines` → `Notifier`(배정·미배정) |
| 담당자 검토 | `ReviewActionController` → `ReviewService.inputAnswer/confirmAnswer/completeAllocation/complete` | `ReviewerAccessPolicy`(배정자만) → `Review` 도메인 메서드 → 모두 완료 시 `SecurityRequest.completeReviews`(→3단계) |
| 보완·차수 반환·종결 | `SupplementController`, `StageReturnController` → `SupplementService`, `StageReturnService` | 상태 전이 + `AnswerService.markNeedsReview` + 기한 재산정 + 알림 |
| 제출(3→4) | `SubmissionController` → `SubmissionService.submit` | `SubmissionChecker`(필수·미확정) + `ReviewFactsProvider`(보완·협의) → `SecurityRequest.submit` → 불변 스냅샷 `Submission` → 멱등성 키 |
| 최종 확인·확정 | `FinalConfirmationController` → `FinalConfirmationService` | 요구 분야 기준 INFRA 선행 → `SecurityRequest.confirm` → `RequestConfirmedEvent` → `FirewallRuleService.onConfirmed` |
| 서류 출력 | `GeneratedDocumentController` → `DocumentService.generate/download` | `DocumentRenderer`(섹션·Markdown) → `DocumentExporters` → `MarkdownExporter`/`PdfExporter` → 다운로드 감사 |
| AI 호출(모든 용도) | 호출 서비스 → `LlmGateway`(= `AuditingLlmGateway`) → `LlmBackend`(`SpringAiLlmGateway` live / `FakeLlmGateway` fake) | `LlmConfigurationSelector`(용도·priority·키 환경변수 존재) → 감사(구성·용도·대상·소요 시간, 원문 없음) |
| 기한 알림 | `DeadlineReminderScheduler`(DB cron, Asia/Seoul) 또는 `POST /api/deadline-reminder-runs` → `DeadlineReminderService` | 활성 기한 훑기 → 1영업일 전/경과 → `Notifier`(event_key 로 1회) |
| 처리할 일·요청함 | `TodoController`, `ReviewActionController.inbox` → `TodoService`, `ReviewService.inbox` | 업무 상태에서 계산(알림 수 아님), `ReviewInboxFilter` |

## 4. 바꿀 때 지켜야 하는 틀

### 4.1 새 기능 체크리스트

1. **규칙 확인**: FUNCTIONAL_SPEC 의 불변식·상태표. 명세와 다르게 하면 `docs/DEVIATIONS.md` 에 한 줄, 구조 결정이면 `docs/adr/` 한 장.
2. **DB**: Flyway 파일 `V<연월일시분>__desc.sql` 추가(머지된 파일 수정 금지). FK 없음, PK `<단수형>_id`, 공통 컬럼 `created_at/updated_at`. JPA 는 `ddl-auto: validate` 라 엔티티와 어긋나면 기동 실패.
3. **도메인**: 상태 전이는 엔티티 메서드(setter 금지), 규칙 위반은 `DomainRuleViolationException`(422). 엔티티는 다른 컨텍스트 엔티티를 참조하지 않고 ID 만.
4. **서비스**: 권한 검사(`MemberPrincipal.requireAnyRole`, `RequestAccessPolicy`, `ReviewerAccessPolicy`) → 도메인 호출 → `AuditRecorder.record`(같은 트랜잭션) → 필요 시 `Notifier`.
5. **API**: `/api/<복수형-kebab>`, 상태 전이는 하위 자원 POST / 단일 하위 자원 PUT, 오류는 ProblemDetail. 컨트롤러는 domain·repository 를 import 하지 않는다(ArchUnit). 열거형 파라미터는 String 으로 받아 400 으로 변환.
6. **테스트**: 도메인 단위 테스트(프레임워크 없음) + API 테스트(`@Tag("docker")`, Testcontainers). 같은 DB 를 여러 테스트가 공유하므로 seed 상태를 바꾸면 끝에 되돌린다.
7. **문서**: `README.md` API 표·ERD, `docs/api/openapi.yaml` 재생성(`-PexportOpenApi=true`), 이 문서의 표.

### 4.2 하면 안 되는 것

- 실제 내부 정보(IP·서버명·자산·개인정보)를 코드·테스트·문서에 넣는 것 — 전부 합성 데이터.
- 비밀값을 코드·DB 에 넣는 것 — `llm_configurations.api_key_env` 처럼 **환경변수 이름**만.
- 프롬프트·응답 원문을 감사·로그에 남기는 것(참조 ID 만).
- AI 값을 확정 사실처럼 저장하는 것 — AI 는 `AI_INFERRED`·미확정으로만 쓰고 사람이 확인한다.
- Lombok 허용 목록 밖(`lombok.config` 가 컴파일 오류로 막음), Spring AI 타입을 `llm.adapter` 밖에서 쓰는 것(ArchUnit).

### 4.3 확장 포인트(코드 수정 없이 또는 최소 수정으로)

| 바꾸고 싶은 것 | 어디서 |
|---|---|
| 서류 유형 추가, 질문·필수 여부·적용 서류, 협의 분야(INFRA/SECURITY 중 필요한 것), 출력 형식 허용, 기한 영업일·공휴일 | 게시본 데이터 — `PUT /api/configurations/{id}` 초안 → `POST …/publications`(ADMIN). 코드 수정 없음 |
| 새 출력 형식(HWPX 등) | `ExportFormat` 값 + `DocumentExporter` 구현 1개(자동 등록). ADR 0007 |
| 새 LLM 공급자 | OpenAI 호환이면 DB 구성 행만(`POST /api/llm-configurations`). 비호환 API 면 `LlmBackend` 구현 추가 |
| 새 감사 행위·알림 종류 | `AuditAction`/`NotificationKind` 값 추가 + 호출 |
| 새 외부 알림 채널(카카오워크 API 등) | `NotificationChannel` 구현 1개(자동 등록, `enabled()` 로 설정 유무 표현) |
| 기한 알림 시각 | `PUT /api/deadline-reminder-schedule`(DB, 재시작 불필요) |
| 확정 시 새로운 행위(예: 대장 기록) | `DocTypeDefinition` 에 행위 플래그 추가 + `RequestConfirmedEvent` 리스너 |
| 절차 단계·순서 변경 | 코드(상태 기계) — P3. 워크플로 정의 데이터화는 미착수 |

## 5. 테스트·빌드

- `gradlew spotlessApply check`: 포맷 → 컴파일 → 단위/API 테스트(Docker 필요) → ArchUnit. 테스트 131건(2026-11-08).
- Docker 없는 환경: `-PexcludeTags=docker`. 다른 출력 폴더: `-PbuildDirOverride=<경로>`. API 계약 재생성: `-PexportOpenApi=true`.
- 실제 모델 호출 검증: 환경변수 `OPENROUTER_API_KEY`(선택 `OPENROUTER_MODEL`)가 있을 때만 `AiProposalLiveTest` 가 실행된다. 키는 셸 환경변수로만.
- LLM 모드: `LLM_MODE=fake` 면 네트워크 없이 고정 응답(테스트 기본), `live` 면 `llm_configurations` 중 키 환경변수가 설정된 첫 구성.
- CI: `.github/workflows/check.yml`(Temurin 25, `gradlew check`, Testcontainers).

## 6. 아직 없는 것(의도된 범위 밖)

양식(템플릿) 편집과 템플릿 기반 출력, HWPX 출력 변환기, 서류 API 전달 채널, 암호·배포용 HWP 복호화, SSO, 절차 단계·순서의 데이터화.
화면에 "구현됨"으로 보이지 않게 한다(지침).

2026-11-08 추가로 들어간 것(P2·P3 일부): 기존 사업 건에 서류 추가, AI 서류 유형 제안(`llm.DocTypeSuggestionService`), 방화벽 규칙 연장
(`policy.FirewallRuleRenewalService` — 규칙 참조 새 신청), 보완 대기 중 협의 기한 정지·재개(`Deadline.pause/consumePause`),
외부 알림 채널(`notification.channel` — `NotificationChannel` 구현: SMTP 최소 클라이언트·웹훅, 환경변수 없으면 꺼짐), HWP 5.0 텍스트 추출(`llm.HwpTextExtractor`, POIFS).
