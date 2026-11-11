# Security-Advisor-Back 구조 안내 (사람·AI 에이전트 공용)

보안길잡이(SecurityAdvisor) 백엔드를 처음 받은 사람이나 AI 가 **코드를 이어서 개발할 수 있도록** 쓴 문서다.
이 문서 하나로 "지금 코드가 어떻게 생겼는가, 왜 그렇게 만들었는가, 명세와 어디가 다른가"를 알 수 있게 했다.

마지막 갱신: 2026-11-11 (커밋 d668c16 기준). 2026-11-11 문서 정리에서 ADR 13건·명세 차이 기록·작업별 검증 기록을
이 문서의 4·5·6장으로 요약해 옮기고 원본 파일은 지웠다. 원문이 필요하면 git 이력의 `d668c16` 에서 `docs/adr/`,
`docs/DEVIATIONS.md`, `docs/*_2026111*.md` 를 꺼내 본다.

> **읽는 법.** 1~3장과 7~8장은 현재 코드를 보고 썼다. 4~6장은 지운 기록 문서를 요약한 것이며, 요약하면서 코드와
> 한 줄씩 다시 대조하지는 않았다. 코드와 다르면 코드가 맞다 — 고치면서 이 문서도 함께 고친다.

## 0. 이 저장소의 문서

| 문서 | 내용 |
|---|---|
| `README.md` | 저장소 역할, 실행 방법, 환경변수, 통합 배포 반영 절차 |
| `docs/ARCHITECTURE.md` | 이 문서 — 구조, 흐름, 설계 결정, 명세와 다른 점, 기능별 동작 규칙 |
| `docs/API.md` | API 경로 146개와 한 줄 설명, 공통 규약(인증·CSRF·오류·revision) |
| `docs/DATABASE.md` | DB 규칙과 ERD(테이블 35개, 마이그레이션에서 생성) |
| `docs/api/openapi.yaml` | API 계약(테스트가 생성하는 파일, 손으로 고치지 않는다). 생성 방법은 `docs/api/README.md` |
| `docs/knowledge/` | 공개 지식 조각 데이터(JSON)와 설명. 넣는 방법은 `scripts/load-knowledge-snippets.ps1` |
| `FUNCTIONAL_SPEC.md` | 업무 규칙 원본 — 역할, 4단계 절차, 불변식 36개, 상태. **v1.12(2026-11-07) 사본** |
| `AGENTS.md` | 개발 규칙 원본 — 스택, 구조, 이름, RESTful API, Git. **v1.17(2026-11-07) 사본** |
| `CODING_STYLE.md`, `SECURE_CODING.md` | 코드 작성 규칙(Google 스타일, KISA 시큐어코딩) |

규칙 문서 4종은 팀 문서의 2026-11-07 사본이다. 그 뒤 팀 원본은 더 개정됐고(FUNCTIONAL_SPEC v1.13, AGENTS v1.19),
사본 안의 `PROJECT_CONTEXT.md`·`PROJECT_RULES.md`·`CONTRIBUTING.md`·`design.md`·`claude/INSTRUCTIONS.md` 링크는
이 저장소에서 지워서 열리지 않는다(팀·대회 운영 문서와 화면 디자인 기준이라 백엔드 인계에서 뺐다).
**구현이 FUNCTIONAL_SPEC 과 다른 곳은 5장에 있다 — 명세만 읽고 코드를 "고치지" 말 것.**

## 1. 한 장 요약

- Spring Boot 4.1 / Java 25 / Gradle(Kotlin DSL) / PostgreSQL 17 + Flyway / Spring Data JPA / Spring AI 2.0(OpenAI 호환 어댑터 1종) / PDFBox.
- 단일 모듈, 패키지 = 바운디드 컨텍스트: `kr.securityadvisor.<컨텍스트>.{controller,dto,service,domain,repository}`.
- 운영 기관은 설치당 1곳(기관 테이블 없음, `.env`). 역할 4종(APPLICANT·SECURITY·INFRA·ADMIN)은 세션의 `MemberPrincipal` 에 있고 권한 판단은 서비스 계층이 한다.
- 업무 절차: 1 작성 → 2 담당자 협의(INFRA 선행 → SECURITY) → 3 사용자 최종 검토 → 4 제출·최종 확인·확정. 상태는 `SecurityRequest`(단계×상태)가 갖고, 전이는 도메인 메서드로만.
- **세 가지가 "게시본"으로 버전 관리되고 사업 건이 만들 때의 버전에 고정된다**: 질문·기한 설정(`publications`), 지식(`knowledge_publications`). AI 모델 구성만 고정하지 않고 다음 호출부터 적용한다(이력은 `llm_configuration_histories`).
- 서류 유형·질문·기한 정책·공휴일·출력 형식은 게시본 데이터다. 코드는 "행위 속성"(협의 분야, 규칙 생성 여부)으로만 분기한다.
- AI 는 제안만 한다. AI 값은 `AI_INFERRED`·미확정으로 저장되고 사람이 확인해야 제출된다. 기관 사실(서버·IP)은 신청자용 AI 에 넘기지 않는다.
- 감사 이력은 추가 전용이며 업무 변경과 같은 트랜잭션. 알림·조회 감사·AI 호출 감사는 별도 트랜잭션이라 실패해도 업무를 막지 않는다.

## 2. 컨텍스트 지도

| 컨텍스트 | 맡는 것 | 테이블 |
|---|---|---|
| `common` | 오류 모델(ProblemDetail), 보안 설정·세션·CSRF, 요청 ID, 게시본 스키마 값 객체(`FieldSchema`·`DocTypeDefinition`·`FieldDefinition`·`DocTypeCode`·`ExportFormat`·`ReviewField`·`ValueSource`), 영업일 달력, JSON 코덱 | — |
| `installation` | 운영 기관 설정값(`GET /api/installation`) | — |
| `audit` | 감사 이력 기록(`AuditRecorder`)·권한별 페이지 조회 | audit_events |
| `auth` | 계정·로그인·잠금·회원가입·역할 부여·세션 세대, 다른 컨텍스트용 조회 창구 `MemberDirectory` | members |
| `configuration` | 질문·기한 설정 초안 편집·비교·게시, 게시본 조회(`PublicationService`) | configurations, publications |
| `request` | 사업 건·신청·답변·제출본·기한·업로드 파일·최종 서술·서버/통신 구성·협의 기준본과 변경 기록·기한 알림 스케줄 | business_cases, security_requests, answers, submissions, deadlines, uploaded_files, final_drafts, infrastructure_plans, consultation_records, reminder_schedules |
| `assignment` | 분야별 담당자 목록·자동 배정·재배정·인계 필요 판정 | reviewer_pools, assignments |
| `review` | 협의·보완·차수 반환·종결·최종 확인·확정·요청함·처리할 일 | reviews, supplements, stage_returns, final_confirmations |
| `knowledge` | 공개 지식 조각(전문검색)·기관 비공개 정보·항목 도움말, 초안과 게시본 | knowledge_snippets, private_contexts, knowledge_drafts, knowledge_publications |
| `llm` | 모델 구성·이력·연결 시험, 공통 게이트웨이(감사 래퍼 + Spring AI/fake), 신청자 인터뷰, AI 항목 제안, 담당자 질의, 문서 요약, 협의 요약, 서버 구성 제안 | llm_configurations, llm_configuration_histories, llm_connection_tests, interview_turns, inquiries, inquiry_messages, analysis_jobs |
| `document` | 서류 렌더링(섹션·Markdown)·출력 형식 변환(Markdown·PDF)·AI 최종 초안 생성 | generated_documents |
| `policy` | 방화벽 규칙 생성·적용 기록·회수·연장·만료 계산 | firewall_rules |
| `notification` | 화면 안 알림(`Notifier`) + 외부 채널(`NotificationChannel`: SMTP·웹훅) | notifications |
| `insight` | 업무 지표(조회 시 계산, 관리자 전용) | — |

`procedures` 테이블은 초기 마이그레이션에만 있고 지금 쓰는 엔티티가 없다.

의존 방향(패키지 import 기준, 2026-11-08 에 확인한 그림 — 이후 추가된 코드는 ArchUnit 이 순환만 막고 있으며 이 그림을 다시 그리지는 않았다):

```
common ← 전부           audit ← 거의 전부         auth ← assignment, review
configuration ← request, review, knowledge, llm, document, policy, insight
request ← review, llm, document, policy, insight      assignment ← request, review
review ← llm, policy, insight      knowledge ← llm, document      llm ← document
notification ← request, review     installation ← document
```

순환은 `architecture/ArchitectureTest.featuresHaveNoCycles` 가 막는다. request→review 는 금지이므로 request 가 review 의 사실이 필요하면
**포트**를 쓴다: `request.service.ReviewFactsProvider`(인터페이스) ← `review.service.ReviewFactsAdapter`(구현).

### 컨텍스트 간 연결 방식

| 방식 | 예 |
|---|---|
| ID + 공개 서비스 호출 | review 가 `AssignmentService.autoAssign/activeAssignee`, `AnswerService.inputByStaff` 를 호출 |
| 포트/어댑터(역방향 의존 회피) | `ReviewFactsProvider` / `ReviewFactsAdapter` |
| 같은 트랜잭션의 동기 ApplicationEvent | `AnswerChangedEvent`(request→review: 완료된 협의 재검토, 최종 서술 확인 무효화), `RequestConfirmedEvent`(review→policy: 방화벽 규칙 생성) |
| 커밋 후 비동기 이벤트 | `DocumentAnalysisService.AnalysisQueued`(@Async + AFTER_COMMIT) |
| 조회 전용 창구 | `auth.service.MemberDirectory`, `review.service.RequiredReviewFields` |

## 3. 주요 흐름을 코드로 따라가기

| 흐름 | 진입 | 거치는 곳 |
|---|---|---|
| 사업 건 생성 | `BusinessCaseController` → `BusinessCaseService.create` | 현재 설정 게시본·지식 게시본 고정 → `BusinessCase.create` + 유형별 `SecurityRequest.create` + 공통 항목 자리 답변 → 감사 |
| 문서 업로드·요약 | `UploadedFileController`, `AnalysisJobController` → `DocumentAnalysisService` | 내용 서명 검사 → 텍스트 추출(`TextExtractor`) → SUMMARY 호출(비동기) → "확인 필요" 상태. `AnalysisApplicationService` 가 요약을 사업 개요 제안으로 반영 |
| 신청자 인터뷰 | `InterviewController` → `InterviewService` | 항목 설명(ASK)·답변 후보(SUGGEST) 저장 → `…/applications` 로 후보를 미확정 답변으로 적용 |
| 답변·AI 제안 | `SecurityRequestController`/`BusinessCaseController` → `AnswerService` / `AiProposalService` | 항목별 권한·형식 검사 → `Answer` 도메인 메서드(출처 전이) → `AnswerChangedEvent` |
| 협의 요청(1→2) | `ReviewController` → `ReviewService.requestReview` | `RequiredReviewFields` → `Review` 생성 → 자동 배정 → 협의 기한 시작 → 협의 기준본 저장 → 알림 |
| 담당자 검토 | `ReviewActionController` → `ReviewService.inputAnswer/confirmAnswer/complete` | `ReviewerAccessPolicy`(배정자만) → `Review` 도메인 메서드 → 모두 완료 시 3단계. `PUT …/allocation-completion` 은 경로만 남아 있고 항상 422 다(5장 22번) |
| 서버·통신 구성 | `InfrastructurePlanController`, `InfrastructureProposalController` | 임시 저장 / 확인 저장, AI 서버 사양 제안(자동 저장 안 함) |
| 협의 결과 비교·요약 | `ConsultationComparisonController`, `ConsultationSummaryController` | 기준본 vs 현재 값 vs 변경 기록, DOCUMENT 모델 요약(근거 E번호 검증) |
| 담당자 AI 질의 | `InquiryController` → `InquiryService`, `InquiryContextService` | 신청 답변·업로드 원문 일부·요약·공개 지식·기관 사실 수집 → INQUIRY 호출 → 근거 태그 검증·내부 정보 가림 |
| 보완·차수 반환·종결 | `SupplementController`, `StageReturnController` | 상태 전이 + 재검토 표시 + 기한 재산정 + 알림 |
| 최종 서술(3단계) | `FinalDraftController`, `document.FinalDraftGenerationService` | AI 생성 또는 직접 작성 → 신청자 확인 → 제출본에 복사 |
| 제출(3→4) | `SubmissionController` → `SubmissionService.submit` | `SubmissionChecker`(필수·미확정·형식) + `ReviewFactsProvider` → `SecurityRequest.submit` → 불변 스냅샷 `Submission`(멱등성 키) |
| 최종 확인·확정 | `FinalConfirmationController` → `FinalConfirmationService` | 요구 분야 순서대로 확인 → `SecurityRequest.confirm` → `RequestConfirmedEvent` → `FirewallRuleService.onConfirmed` |
| 서류 출력 | `GeneratedDocumentController` → `DocumentService` | `DocumentRenderer` → `DocumentExporters` → `MarkdownExporter`/`PdfExporter` → 다운로드 감사 |
| AI 호출(모든 용도) | 호출 서비스 → `LlmGateway`(= `AuditingLlmGateway`) → `LlmBackend`(`SpringAiLlmGateway` live / `FakeLlmGateway` fake) | `LlmConfigurationSelector`(용도·우선순위·키 환경변수) → 감사(구성·용도·대상·소요 시간, 원문 없음) |
| 지식 관리 | `KnowledgeController`, `KnowledgePublicationController` | 조각·기관 정보 편집은 초안에만 → 비교 → 게시(새 버전) |
| 설정 관리 | `configuration` 컨트롤러 | 초안 저장 → 비교 → 게시(새 버전) |
| 모델 구성 관리 | `LlmConfigurationController`, `LlmConnectionTestController` | 비교 → 적용(이력 기록) → 복원, 연결 시험 |
| 기한 알림 | `DeadlineReminderScheduler`(DB cron, Asia/Seoul) 또는 `POST /api/deadline-reminder-runs` | 활성 기한 훑기 → 1영업일 전/경과 → `Notifier`(event_key 로 1회) |
| 처리할 일·요청함 | `TodoController`, `ReviewActionController.inbox` | 업무 상태에서 계산(알림 수 아님) |

AI 에게 주는 지시문(시스템 프롬프트)은 설정이 아니라 각 서비스 클래스의 `SYSTEM` 상수다: `InterviewService`, `AiProposalService`,
`DocumentAnalysisService`, `InquiryService`, `DocTypeSuggestionService`, `document.DocumentService`, `document.FinalDraftGenerationService`.
공통 블록([기관 사실]·[공개 지식])은 `llm.adapter.PromptAssembler` 가 붙인다.

## 4. 설계 결정 요약

코드 주석의 "ADR 0006" 같은 번호는 아래 표의 번호다.

| 번호 | 결정 | 이유·주의 |
|---|---|---|
| 0001 | **스택과 스키마 규칙.** Flyway 마이그레이션만으로 스키마 변경(`V<연월일시분>__desc.sql`, 머지된 파일 수정 금지), JPA `ddl-auto: validate`. **외래 키 제약 없음** — 참조 무결성은 도메인·서비스가 지키고 참조 컬럼엔 인덱스만. 업무 불변식 CHECK·UNIQUE 는 유지. PK 는 `<단수형>_id`(`members` 는 `username`). 반구조 데이터는 jsonb(엔티티는 JSON 문자열만 들고 해석은 service). 변경 가능한 애그리거트는 `revision`(`@Version`) + 쓰기 API 의 `expectedRevision`(불일치 409). 감사 이력은 DB 트리거가 UPDATE·DELETE 거부 | 운영 단순화·성능(팀 결정). Lombok 은 `@Getter`·`@RequiredArgsConstructor`·`@NoArgsConstructor(PROTECTED)`·`@Slf4j` 만(`lombok.config` 가 강제) |
| 0002 | **세션 쿠키 인증 + SPA 방식 CSRF.** JWT 미사용. `XSRF-TOKEN` 쿠키 ↔ `X-XSRF-TOKEN` 헤더, 로그인·회원가입도 검사. 회원가입은 항상 APPLICANT. 로그인 5회 실패 시 15분 잠금(429). 비밀번호 bcrypt. 시연 계정은 `DEMO_ACCOUNT_PASSWORD` 가 있을 때만 생성. 남의 자원은 404 로 숨김 | 테스트의 `csrf()` 후처리기 때문에 쿠키 발급 테스트는 별도 컨텍스트(`CsrfCookieTest`) |
| 0003 | **DDD 구조.** 패키지 = 컨텍스트, domain 은 프레임워크 비의존(JPA 애너테이션만), 상태 전이는 도메인 메서드, 컨텍스트 간은 ID + 공개 서비스. 역방향이 필요하면 포트 인터페이스 또는 같은 트랜잭션의 동기 이벤트만. AI 호출 유스케이스는 llm 에 모음. 답변은 별도 리포지토리, 기한은 request | 이벤트 소싱·CQRS 는 도입하지 않았다 |
| 0004 | **RESTful API 와 오류 모델.** `/api/<복수형-kebab>`, 소속은 한 단계까지. 상태 전이는 하위 자원 POST 또는 단일 하위 자원 PUT(`status` 직접 변경 API 없음). 제출은 `Idempotency-Key` 헤더. 오류는 ProblemDetail: 400 형식 · 401 · 403 권한 · 404 없음/남의 자원 · 409 revision/중복 · 422 업무 규칙 · 429 잠금 · 503 AI 등 외부 의존. 응답은 dto record 만 | 계약은 `docs/api/openapi.yaml`, Back 이 소유 |
| 0005 | **서류 유형은 코드가 아니라 게시본 데이터**(`docTypes`). `DocType` enum 없이 `DocTypeCode` 문자열, DB CHECK 없음. 유형마다 `reviewFields`(협의 분야)·`generatesFirewallRules`·`exportFormats` 등 행위 속성. 유형 코드는 게시 후 불변, 삭제 없이 비활성화만 | 새 서류 추가 = 게시본 한 줄. 새 "종류의 행위"가 필요한 서류는 여전히 코드 작업 |
| 0006 | **지식 검색은 벡터가 아니라 태깅 + Postgres 전문검색.** `field_keys` 로 1차 필터, `tsvector`(simple)·`ts_rank` 순위, 자유 질의는 접두 매칭. pgvector·임베딩 없음. 기관 비공개 정보는 구조화 레코드로 두고 서버가 역할로 걸러 프롬프트의 사실 블록에만 넣음. 모델 출력의 IP·대역은 `InternalInfoGuard` 가 대조 | 필요해지면 같은 테이블에 임베딩 열을 더해 하이브리드로(검색 인터페이스 `KnowledgeSearch` 유지) |
| 0007 | **출력 형식은 변환기 플러그인.** `ExportFormat` 값 + `DocumentExporter` 구현 1개 = 형식 1개. 유형별 허용 형식은 게시본 데이터. 구현 없는 형식은 `implemented=false`·다운로드 503. PDF 는 PDFBox 직접 조판, 번들 글꼴(Noto Sans CJK KR 서브셋, OFL) | HWPX 는 값만 있고 변환기 없음. API 전달·메일 발송은 형식이 아니라 "전달 채널"이라 만들지 않았다 |
| 0008 | **최종 서술 확인과 제출본 보존.** 최종 서술은 request 의 `FinalDraft`. AI 생성·직접 작성 모두 미확인으로 시작, 신청자 본인이 3단계에서 확인. 답변이 바뀌면 확인 무효. 생성한 초안이 있으면 최신·확인 상태여야 제출 가능. 제출본 JSON 에 본문·AI 원문·출처·모델·확인자를 복사 | 서술 없는 신청은 답변만 제출하는 호환 경로가 남아 있다 |
| 0009 | **신청·감사 목록은 DB 에서 권한·검색·정렬·페이지 처리**(읽기 전용 네이티브 조회). 감사 페이지는 `throughId` 로 조회 시작 이후 추가된 기록을 제외. 신청자에게 로그인·다운로드·AI 호출 등 보안 메타는 숨김 | 별도 읽기 모델은 없다. 정렬은 허용 목록 상수만 |
| 0010 | **조회는 "현재 역할의 활성 배정"만.** 담당자는 배정된 신청과 그 사업 건만, 관리자는 전체 조회(검토 권한은 아님), 지표는 관리자 전용. 조회 불가는 404. 계정의 역할·활성 상태가 바뀌면 `session_version` 을 올려 기존 세션을 401 로 끊음(매 요청 검사) | FUNCTIONAL_SPEC 의 "같은 분야 미배정 담당자 조회 허용"과 다르다(5장) |
| 0011 | **알림은 현재 수신 권한으로 조회, 처리할 일은 별개.** 알림에 신청 ID·발송 당시 역할을 기록하고 접근을 잃으면 숨김(삭제 아님). 업무 커밋 뒤 별도 트랜잭션으로 저장. `GET /api/todos` 는 현재 업무 상태에서 계산 | 프로세스 중단 시 재전송을 보장하는 발송 대기열은 없다 |
| 0012 | **지식은 초안과 게시본 분리.** `knowledge_snippets`·`private_contexts` 는 편집 초안이고 게시해야 업무에 쓰인다. 사업 건은 생성 시 지식 게시본에 고정. 비공개 사실의 값은 고정 사본, 열람 권한은 고정 사본과 현재 게시본을 모두 만족해야 함. 조각의 `fieldKeys` 는 현재 설정 게시본의 실제 질문 키만 허용 | 게시본은 JSON 스냅샷을 조회하므로 자료가 커지면 별도 인덱스가 필요 |
| 0013 | **모델 구성은 저장이 곧 적용**(사업 건에 고정하지 않음). 변경·복원마다 전체 값·작성자·사유를 이력에 기록(수정·삭제 불가). 키는 `LLM_ALLOWED_KEY_ENVS` 에 등록한 환경변수 이름만 참조. 연결 시험은 고정 합성 문장·최대 512토큰·30초·재시도 없음. Spring AI 2.0.1 은 `/v1` 을 자동으로 붙이지 않아 호스트 루트와 OpenRouter `/api` 주소에만 호출 시 `/v1` 을 붙인다 | 시험 `201` 은 연결 성공이 아니다 — 응답의 `status`·`outcome` 을 본다 |

## 5. 명세(FUNCTIONAL_SPEC)와 다른 점

구현이 명세와 **의도적으로** 다른 곳이다. 팀 문서에서는 "결정 대기"로 관리하던 항목이라, 명세 쪽이 나중에 바뀌었을 수 있다.
코드 주석의 "DEVIATIONS #21" 같은 번호는 아래 번호다.

| # | 구현 | 명세 |
|---|---|---|
| 1 | 서류 유형이 게시본 데이터(결정 0005) | DocType 은 FIREWALL / SECURITY_REVIEW 2종 enum 전제 |
| 2 | 외래 키 제약 없음(결정 0001) | 8-1 스키마 제안은 FK 표기 |
| 4 | 답변은 별도 리포지토리(사업 건·신청의 컬렉션으로 매핑하지 않음) | 리포지토리는 애그리거트 루트 단위 |
| 5 | 기한(`Deadline`) 엔티티·서비스가 request 컨텍스트에 있음 | 위치 미지정 |
| 6 | 배정·기한 이벤트도 감사 대상 유형은 SECURITY_REQUEST. 답변 변경은 값 없이 항목 키·출처만 기록 | 대상 유형·ID 기록 |
| 8 | 담당자 입력·확인은 review API(`/api/reviews/{id}/answers/{fieldKey}`), 저장은 request 의 `AnswerService` | 경로 미지정 |
| 9 | 답변 변경 → 협의 재검토는 동기 `ApplicationEvent` | 컨텍스트 간 연결은 ID + 공개 서비스 |
| 10 | 차수 반환·종결은 그 신청에 활성 배정된 담당자만(관리자 불가) | "담당자" |
| 11 | 제출 전 검사의 협의 사실은 포트로 받음 | — |
| 12 | (해소) 문서함 메모리 필터 → 결정 0009 로 DB 페이지 조회 | 목록 API 는 page·size |
| 13 | 답변 변경에 따른 협의 재검토는 **2단계에서만**. 3단계 보충은 협의 상태를 바꾸지 않고 4단계 최종 확인에서 검증 | 불변식 7 — 단계 범위 없음 |
| 14 | 지식 검색에 pgvector 미사용(결정 0006) | F-09 "PGVector 색인" |
| 15 | LLM 공급자 구현은 OpenAI 호환 API 하나. Spring AI starter 대신 모델 모듈을 직접 써서 DB 구성으로 런타임 생성 | 공급자 2종 |
| 16 | 알림은 업무 서비스가 `Notifier` 를 직접 호출 + 별도 트랜잭션 | 발송 방식 미지정 |
| 17 | 담당자 AI 질의·문서 요약이 llm 컨텍스트에 있음 | 질의는 review, 분석 작업은 request |
| 18 | 서류 출력은 Markdown·PDF 까지 | "미리보기·다운로드(형식·용량)" |
| 19 | 상시 방화벽 규칙의 재검토일 = 시작일 + 12개월 고정, 만료 예고 30일 고정(`RulePeriod`·`FirewallRuleService`) | 기관 설정값 |
| 20 | INFRA 선행·확정 권한은 "그 서류가 요구하는 분야" 기준. 한 분야만 요구하면 그 분야 담당자가 확정 | 확정은 SECURITY 담당자 |
| 21 | 사업 건·유형당 신청 1건 제약은 진행 중 신청에만(부분 유일 인덱스). 확정·종결·철회 뒤 같은 유형 재신청 가능 | `UNIQUE(business_case_id, doc_type)` |
| 22 | **"할당 완료" 단계가 없다.** `PUT …/allocation-completion` 은 422("협의 완료로 통합")만 돌려주고, SECURITY 협의 완료는 INFRA **협의 완료** 뒤에만 된다 | 할당 완료와 협의 완료는 별도 단계 |
| 23 | 2단계에서 정보보안 담당자가 서버·통신 구성을 고치면 완료된 인프라 협의가 재검토로 바뀌고, 재협의 전에는 보안 협의 완료가 막힌다 | — |
| 24 | 조회는 활성 배정 기준(결정 0010) | 같은 분야 미배정 담당자 조회 허용 |
| 25 | 11.08 기능 동결 뒤에 들어간 기능: 서버·통신 구성 편집과 AI 제안, 설정·지식·모델 관리, 협의 비교·요약, 세션 연장, 사업 건에 서류 추가, 규칙 연장, 기한 정지, 메일·웹훅 채널 | P2·P3 또는 범위 밖 |

3·7번(기본 키 이름 규칙, Java 25)은 이미 규칙 문서와 일치해 뺐다. 22~25번은 2026-11-10 이후 팀 기록에서 옮긴 것이다.

## 6. 기능별 동작 규칙

지운 작업 기록에서 "코드를 고칠 때 알아야 하는 규칙"만 옮겼다. 검증 수치·시연 환경 기록은 옮기지 않았다.

### 답변 권한과 형식

- 직접 입력은 그 항목의 작성 역할(`askRole`)만 한다. 관리자 우회 없음. 신청자 확인은 신청자 항목의 AI 답변만, 담당자 확인은 자기 분야와 신청자 항목만.
- AI 추천 적용은 신청자 항목만, 형식 검사 후 `AI_INFERRED`·미확정.
- 형식: TEXT/LONG_TEXT 1~4000자, BOOLEAN(`true`/`false`/`예`/`아니요`), NUMBER 십진수, DATE_RANGE `YYYY-MM-DD~YYYY-MM-DD`(종료일 생략·`상시`·`종료일 없음` 허용), IP_ADDRESS(IPv4·IPv6·CIDR), PORT(1~65535, 범위), PROTOCOL(TCP·UDP·ICMP·ICMPV6·ESP·AH·GRE). IP·포트·프로토콜은 쉼표·줄바꿈 목록 최대 100개, 리터럴만 검사.
- "미해당"은 선택 항목만(`notApplicable: true`, `answers.not_applicable`). 값과 함께 보내면 422. 일반 값을 다시 저장하면 해제.
- 일괄 저장(`PATCH …/answers`, 담당자는 `PATCH /api/reviews/{id}/answers`)은 한 트랜잭션에서 전부 성공하거나 전부 취소.
- 권한 위반 403, 형식·업무 규칙 422, DTO 오류 400, revision 불일치 409.

### 신청자 인터뷰

- `POST /api/business-cases/{caseId}/interview-turns` `{fieldKey, mode: ASK|SUGGEST, text, draft?, expectedRevision}` — 설명·후보만 저장하고 답변은 바꾸지 않는다. `…/{turnId}/applications` 로 후보를 미확정 답변으로 적용.
- AI 에 주는 것: 고정 게시본의 질문, 신청자가 쓴 공통 답변, 업로드 문서의 미확정 요약, 연결된 공개 지식. 기관 비공개 정보와 담당자 항목 값은 주지 않는다.
- 후보 생성 뒤 사업 건·공통 답변·문서 요약이 바뀌면 적용은 409. 다른 신청자는 404, 담당자·관리자는 403.

### 담당자 AI 질의

- 수집: 현재 신청의 답변·사업 정보, 같은 사업의 최근 업로드 3개와 완료된 요약 3개, 공개 지식, 역할로 거른 기관 사실. 원문은 추출 텍스트 앞 20,000자 중 질문 단어 주변 최대 6,000자.
- 근거 태그와 질의 당시 설명을 함께 저장한다. 알 수 없는 태그는 빼고 경고, 출처 없는 IP 는 가린다. 잘못된 AI 응답은 503 이며 질문·답변을 저장하지 않는다.
- 작성자뿐 아니라 현재 역할·활성 배정도 매번 다시 검사한다. 신청 상태는 바꾸지 않는다.

### 서버·통신 구성(infrastructure-plan)

- JSON `schemaVersion: 2` — `servers`, 통신 구간, `operations`, `checklist`, `draft`, `aiPending`. 이전 형식 저장본도 읽는다. 서버는 ID 로 통신 구간에 연결하고 끊어진 연결·중복 식별자는 거부.
- **임시 저장**은 누락을 허용하되 입력된 주소·포트·프로토콜·날짜 형식은 검사하고, 공통·서류 답변에 동기화하지 않는다. **확인 저장**은 서버 필수 항목·완성된 통신 구간·운영 설명·체크리스트 4개를 요구한다.
- 임시 저장이거나 AI 미확인 항목이 남아 있으면 협의 완료·제출이 막힌다.
- 배정된 INFRA 또는 SECURITY 담당자가 협의 단계에서 편집한다(공유 항목 10개만 별도 권한 경로). 보안성검토 서류에도 같은 화면을 쓰지만 방화벽 규칙은 만들지 않는다.
- AI 서버 사양 제안은 제안 API 에 `target: SERVERS`. 값과 인용을 첨부 텍스트·담당자 설명과 대조하고, 자동 저장하지 않는다.

### 협의 결과 비교와 AI 요약

- 협의 요청 때 답변과 구성의 기준본을 추가 전용 `consultation_records` 에 저장하고, 이후 담당자·신청자의 변경 전후 값을 기록한다. 업무 원문이 들어가므로 감사 이력과 분리하고 신청 상세와 같은 조회 권한을 건다.
- `GET /api/security-requests/{id}/consultation-comparison`, `POST …/consultation-summaries`(본인 신청·3단계, `expectedFingerprint`).
- 요약 모델에는 근거 목록을 **E번호**로 주고, 모델은 E번호만 고른다. 서버가 원본 키로 바꿔 저장한다. 목록에 없는 번호·내부 키 직접 반환·근거 없는 IP 는 거부한다(잘라 맞추거나 재시도하지 않는다).
- 입력이 20만 자를 넘으면 요약하지 않고 직접 비교 안내를 돌려준다.

### 모델 응답 처리

- 응답 종료 사유가 `length`/`max_tokens` 로 잘린 응답은 모든 용도에서 저장 전에 거부한다. 추론형 모델(DeepSeek V4.1 Flash 등)은 추론 토큰이 출력 한도를 같이 쓰므로 한도가 작으면 본문이 비거나 JSON 이 잘린다 — 구성의 `max_tokens` 를 올려 해결한다.
- 빈 응답은 503. 프롬프트·응답 원문은 로그·감사에 남기지 않고 구성 참조와 실패 종류만 남긴다.
- 자동 재시도는 없다.

### 설정(질문·기한) 편집·게시

- 설치당 설정 초안 하나. 저장만으로는 업무에 반영되지 않고, 비교 후 게시해야 새 게시본이 된다. 내용이 같으면 게시 거부.
- 기본 질문은 키·형식·역할·범위·적용 서류·필수 여부가 보호된다. 질문 최대 200개, 기한은 1~365 영업일 정수, 공휴일 최대 366개.
- 새 사업 건만 새 게시본을 쓴다. 기존 사업 건과 거기에 나중에 추가한 신청, 이미 계산된 기한은 바뀌지 않는다.

### 담당자 변경과 업무 인계

- 계정 역할·활성 상태·분야 등록이 바뀌어도 기존 배정 행은 지우거나 자동으로 옮기지 않는다. 조회할 때 "재배정 필요"를 판정한다(사유: UNASSIGNED, ACCOUNT_INACTIVE, ROLE_CHANGED, NOT_REGISTERED, POOL_INACTIVE).
- 관리자가 사유를 남겨 재배정한다. 전임 배정은 비활성 이력으로 남고 답변·협의·제출본은 바뀌지 않는다. 종료된 신청의 재배정은 422.
- 자동 배정에서 같은 사업의 기존 담당자가 부적격이면 유효한 기본 담당자로 넘어가고, 없으면 미배정으로 둔다.

## 7. 바꿀 때 지켜야 하는 틀

### 7.1 새 기능 체크리스트

1. **규칙 확인**: FUNCTIONAL_SPEC 의 불변식·상태표, 그리고 이 문서 5장. 명세와 다르게 하면 5장에 한 줄, 구조 결정이면 4장에 한 줄을 더한다.
2. **DB**: Flyway 파일 `V<연월일시분>__desc.sql` 추가(머지된 파일 수정 금지). FK 없음, PK `<단수형>_id`, 공통 컬럼 `created_at/updated_at`. 엔티티와 어긋나면 기동 실패.
3. **도메인**: 상태 전이는 엔티티 메서드(setter 금지), 규칙 위반은 `DomainRuleViolationException`(422). 다른 컨텍스트 엔티티를 참조하지 않고 ID 만.
4. **서비스**: 권한 검사(`MemberPrincipal.requireAnyRole`, `RequestAccessPolicy`, `ReviewerAccessPolicy`) → 도메인 호출 → `AuditRecorder.record`(같은 트랜잭션) → 필요 시 `Notifier`.
5. **API**: `/api/<복수형-kebab>`, 상태 전이는 하위 자원 POST / 단일 하위 자원 PUT, 오류는 ProblemDetail. 컨트롤러는 domain·repository 를 import 하지 않는다(ArchUnit). 열거형 파라미터는 String 으로 받아 400 으로 변환.
6. **테스트**: 도메인 단위 테스트 + API 테스트(`@Tag("docker")`, Testcontainers). 같은 DB 를 여러 테스트가 공유하므로 seed 상태를 바꾸면 끝에 되돌린다.
7. **문서**: `docs/API.md` 표, `docs/DATABASE.md` ERD, `docs/api/openapi.yaml` 재생성(`-PexportOpenApi=true`), 이 문서의 표.

### 7.2 하면 안 되는 것

- 실제 내부 정보(IP·서버명·자산·개인정보)를 코드·테스트·문서에 넣는 것 — 전부 합성 데이터.
- 비밀값을 코드·DB 에 넣는 것 — `llm_configurations.api_key_env` 처럼 **환경변수 이름**만.
- 프롬프트·응답 원문을 감사·로그에 남기는 것.
- AI 값을 확정 사실처럼 저장하는 것.
- 실행하지 않은 검사를 통과했다고 적는 것.
- Lombok 허용 목록 밖, Spring AI 타입을 `llm.adapter` 밖에서 쓰는 것(ArchUnit).

### 7.3 확장 포인트

| 바꾸고 싶은 것 | 어디서 |
|---|---|
| 서류 유형 추가, 질문·필수 여부·적용 서류, 협의 분야, 출력 형식 허용, 기한 영업일·공휴일 | 설정 게시본 — `PUT /api/configurations/{id}` 초안 → `POST …/publications`(ADMIN). 코드 수정 없음 |
| 공개 지식·기관 정보 | 지식 초안 편집 → `POST /api/knowledge-drafts/current/publications`. 데이터는 `docs/knowledge/` |
| 새 출력 형식(HWPX 등) | `ExportFormat` 값 + `DocumentExporter` 구현 1개(자동 등록) |
| 새 LLM 공급자·모델 | OpenAI 호환이면 모델 구성 행만. 비호환 API 면 `LlmBackend` 구현 추가 |
| AI 지시문 | 3장 끝의 `SYSTEM` 상수(코드 수정·재배포 필요) |
| 새 감사 행위·알림 종류 | `AuditAction`/`NotificationKind` 값 추가 + 호출 |
| 새 외부 알림 채널 | `NotificationChannel` 구현 1개(자동 등록, `enabled()` 로 설정 유무 표현) |
| 기한 알림 시각 | `PUT /api/deadline-reminder-schedule`(DB, 재시작 불필요) |
| 확정 시 새로운 행위(예: 대장 기록) | `DocTypeDefinition` 에 행위 플래그 추가 + `RequestConfirmedEvent` 리스너 |
| 절차 단계·순서 변경 | 코드(상태 기계). 데이터화하지 않았다 |

## 8. 테스트·빌드·도구

- `gradlew spotlessApply check`: 포맷 → 컴파일 → 단위/API 테스트(Docker 필요) → ArchUnit. `@Test` 는 약 200개(2026-11-11 소스 기준 — 이 정리 작업에서 실행하지는 않았다).
- Docker 없는 환경: `-PexcludeTags=docker`. 다른 출력 폴더: `-PbuildDirOverride=<경로>`. API 계약 재생성: `-PexportOpenApi=true`.
- 실제 모델 호출 검증: 환경변수 `OPENROUTER_API_KEY` 가 있을 때만 `AiProposalLiveTest` 가 실행된다. 키는 셸 환경변수로만.
- LLM 모드: `LLM_MODE=fake` 면 네트워크 없이 고정 응답, `live` 면 활성 구성 중 키 환경변수가 설정된 첫 구성.
- CI: `.github/workflows/check.yml`(Temurin 25, `gradlew check`, Testcontainers).
- `tools/verify_*.mjs`: 떠 있는 서버를 상대로 권한·세션, 업무 인계, 알림을 HTTP 로 재현하는 검증 스크립트. 합성 계정·신청을 만들므로 시연·검증 환경에서만 쓴다(`DEMO_ACCOUNT_PASSWORD` 환경변수 필요).
- `scripts/load-knowledge-snippets.ps1`: `docs/knowledge/` 의 지식 조각을 지식 초안에 넣고 게시한다(실행 검증 안 됨 — 파일 머리말 참고).

## 9. 아직 없는 것과 알려진 한계

- 없음: 양식(템플릿) 편집과 템플릿 기반 출력, HWPX 출력 변환기, 서류 API 전달 채널, 암호·배포용 HWP 복호화, SSO, 절차 단계·순서의 데이터화, 벡터 검색, 문서 업로드 → 지식 자동 색인, 영속 알림 발송 대기열.
- 지식 조각은 `fieldKeys` 에 실제 질문 키가 있어야 등록된다. 특정 질문에 묶이지 않는 "질의 전용" 조각을 넣을 방법이 없다(`docs/knowledge/README.md`).
- 처리할 일·업무 지표는 조회할 때 메모리에서 계산한다. 이력이 커지면 DB 집계로 바꿔야 한다.
- 실제 SMTP·웹훅 발송, 실제 한글(HWP) 파일 추출은 합성 자료로만 확인했다.
