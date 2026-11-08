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

- 커밋 `10d6c21` 까지: F-01·F-03(사람 입력)·F-04·F-05·F-07·F-11·F-12·F-13·F-14, 로컬 `gradlew check` 106건 통과.
- 작업 트리(미커밋): **llm·knowledge 컨텍스트 1단계** — Spring AI 2.0.1 의존성(`spring-ai-openai`·`spring-ai-client-chat`, starter 미사용), `LlmGateway` + `SpringAiLlmGateway`(live) / `FakeLlmGateway`(fake), `llm_configurations`(F-10 관리 API), `knowledge_snippets`(FTS)·`private_contexts`(F-09), F-03 `POST …/ai-proposals`, `InternalInfoGuard`, ArchUnit "Spring AI 는 llm.adapter 만". ADR 0006, DEVIATIONS #14·#15.
- **첫 빌드는 PC 에서**: Spring AI 의존성이 VM 캐시에 없다. `SpringAiLlmGateway` 의 Spring AI 2.0 API 호출(`OpenAiChatOptions.builder().baseUrl/apiKey/model/temperature/maxTokens/timeout`, `OpenAiChatModel.builder().options()`, `ChatClient.builder(model)`)은 문서 기준으로 작성했고 컴파일 미검증.
- 남은 기능: 담당자 AI 질의(대화 메모리, F-04), F-02 업로드·요약, F-06 서류 초안, F-15 방화벽 규칙, F-16 지표, F-17 알림, F-08 설정 편집, 제출 준비물(GET /api/installation, OpenAPI 파일, compose.yaml, 규칙 문서 복사, ADR 0001~0004).
