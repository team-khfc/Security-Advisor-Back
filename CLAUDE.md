# Security-Advisor-Back — Claude Code 작업 메모

보안길잡이(SecurityAdvisor) 백엔드. 팀 규칙의 원본은 Claude 프로젝트 "보안길잡이"의 문서(PROJECT_CONTEXT·FUNCTIONAL_SPEC·AGENTS·CODING_STYLE·SECURE_CODING·CONTRIBUTING)다.
이 저장소 루트에 아직 그 사본이 없으니, 규칙이 필요하면 사용자에게 해당 문서를 붙여 달라고 한다. 저장소 안의 결정 기록은 `docs/adr/`, 규칙과 다른 결정은 `docs/DEVIATIONS.md`, API·DB 명세는 `README.md`.

## 반드시 지킬 것

- **커밋·push 는 사용자가 "커밋해"라고 할 때만** 한다. 자동 커밋 금지.
- 실제 내부 정보(내부망 IP·서버명·자산·실존 개인정보) 금지 — 코드·테스트·문서 전부 합성 데이터.
- 비밀값은 환경변수만(`.env` 는 git 제외). 코드·DB 평문 저장 금지.
- 실행하지 않은 검사를 통과로 보고하지 않는다. 미검증은 미검증이라고 쓴다.
- DDD 전술 패턴: `kr.securityadvisor.<컨텍스트>/{controller,dto,service,domain,repository}`. 상태 전이는 도메인 메서드(setter 금지), 컨텍스트 간 연결은 ID + 공개 서비스(+ 같은 트랜잭션의 동기 ApplicationEvent).
- RESTful: `/api/<복수형-kebab>`, 상태 전이는 하위 자원 POST / 단일 하위 자원 PUT, 오류는 ProblemDetail(400/401/403/404/409/422).
- DB: Flyway 마이그레이션만(`V<연월일시분>__desc.sql`, 미머지 파일은 제자리 수정), FK 제약 없음, PK 는 `<단수형>_id`, 같은 이름 컬럼 = 같은 형식. JPA 는 `ddl-auto: validate`.
- Lombok 은 허용 목록만: `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)`, `@Slf4j` (`lombok.config` 가 나머지를 막는다).
- 코드 스타일은 Spotless(palantir) — `gradlew spotlessApply` 로 맞춘다.

## 검사 명령

- Docker 가 필요한 테스트(Testcontainers)는 `@Tag("docker")`. Docker 없는 환경에서는 `-PexcludeTags=docker` 로 제외한다.
- 다른 환경에서 같은 소스 트리를 빌드할 때는 `-PbuildDirOverride=<경로>` 로 출력 폴더를 분리한다(PC 의 build/ 결과 보존).
- `.tools/jdk-25`(Linux JDK)·`.gradle-home`(Gradle 캐시)은 오프라인 VM 빌드용 로컬 자원이며 git 제외.

```
gradlew spotlessApply check     # 포맷 + 컴파일 + 단위/통합 테스트(Testcontainers, Docker 필요) + ArchUnit
gradlew test --tests "kr.securityadvisor.auth.AuthControllerTest"
```

## 현재 상태 (2026-11-08)

- 커밋 `3bbd95c` 까지: F-01·F-03·F-04·F-05·F-07·F-09(1차)·F-10·F-11~F-14, 로컬 `gradlew check` 116건 통과.
- 작업 트리(미커밋): 담당자 AI 질의(llm.Inquiry*), F-06 서류(document), `GET /api/installation`, F-15 방화벽 규칙(policy, 확정 이벤트 `RequestConfirmedEvent`), F-02 업로드·요약(request.UploadedFile + llm.AnalysisJob, PDFBox·POI, 비동기 `@TransactionalEventListener`+`TransactionTemplate`), F-17 알림(notification.Notifier, REQUIRES_NEW), F-16 지표(insight), F-08 설정 편집·게시(configuration.Configuration). 마이그레이션 V202611081400~1900. DEVIATIONS #16~#19. VM 에서 Docker 제외 테스트 통과, Docker 테스트는 PC 실행 필요.
- 기능 목록(F-01~F-17)은 1차 범위에서 전부 구현됨(커밋 `1cd5c32`). 제출 준비물 중 완료: `compose.yaml`(db+app, ollama 프로파일), CI(`.github/workflows/check.yml`), ADR 0001~0004, OpenAPI 추출(`OpenApiExportTest`, `-PexportOpenApi=true` 로 `docs/api/openapi.yaml` 생성). `procedure` 예제 패키지는 삭제함(테이블 `procedures` 는 머지된 마이그레이션이라 남김). 남은 것: 규칙 문서 9종 저장소 루트 복사(원본은 Claude 프로젝트 — 사용자 PC 사본 위치 확인 필요), 실제 모델 호출 검증(`AiProposalLiveTest`), PDF 출력(후속).
- 명세 세부 보강(이번 작업): 관리자 역할 부여 API(`PUT /api/members/{username}/role`, `MemberAdminService`), 분야별 담당자 목록 관리 API(`/api/reviewer-pools`, `ReviewerPoolService` — 비활성화는 배정을 바꾸지 않고 재배정 필요 신청 반환), 처리할 일 배지(`GET /api/todo-summary`, `review.TodoService`), 요청함 필터·형제 신청(`GET /api/reviews?status&requestStatus&field&docType&keyword`, `ReviewInboxFilter`). 다른 컨텍스트가 계정 역할을 확인할 때는 `auth.service.MemberDirectory`만 쓴다(Member 엔티티·저장소 직접 사용 금지). 이후 추가: 기한 알림 스케줄러(`DeadlineReminderService`, `POST /api/deadline-reminder-runs`; cron 은 DB `reminder_schedules` + `GET/PUT /api/deadline-reminder-schedule` 로 관리자가 바꾸면 `DeadlineReminderScheduler.apply` 가 재등록 — 환경변수 아님), 미배정→관리자·제출 뒤 공통 정보 변경→담당자 알림, 항목 도움말(`GET /api/field-help/{fieldKey}`), 차수 반환 항목별 사유(`fieldReasons`). F-16 지표 전체(서류 유형별·단계별 영업일·보완 횟수·반환률·1회 통과율·AI 제안 확인율)와 F-14 조회 감사(AI 호출 `AuditingLlmGateway`+`LlmBackend`, 서류 다운로드, 비공개 컨텍스트 조회)도 완료. PDF 출력도 완료(`document.export.PdfExporter`, PDFBox + 번들 글꼴 `resources/fonts/`, 형식별 `DocumentExporter` 플러그인, 서류 유형별 허용 형식은 `DocTypeDefinition.exportFormats` — ADR 0007). 아직 없는 것: 양식(템플릿) 편집, HWPX 변환기(형식 값만 있음), API 전달·메일 발송 채널, MIME 실제 검사.
