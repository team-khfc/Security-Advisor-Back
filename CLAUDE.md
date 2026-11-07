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

## 현재 상태 (2026-11-07, 미커밋 작업 트리)

마지막 커밋 `502223c` 이후 F-04(담당자 검토)·F-05(보완·차수 반환·종결)를 구현했고 아직 커밋하지 않았다. 첫 `check` 실행 결과: 컴파일 통과, 103건 중 20건 실패 → 두 원인은 수정함, 하나는 진단 중.

1. (수정함) `security_requests.stage`·`stage_returns.from_stage/to_stage` SMALLINT ↔ int 검증 실패 → `@JdbcTypeCode(SqlTypes.SMALLINT)`.
2. (수정함) ArchUnit `controllerDoesNotExposeEntity` 가 `org.springframework.data.domain` 까지 잡음 → 패턴을 `kr.securityadvisor..domain..` 으로, `AuditEventController` 의 `targetType` 은 String 으로.
3. (수정함) XSRF-TOKEN 쿠키 테스트 — 원인은 `SecurityMockMvcRequestPostProcessors.csrf()` 가 싱글턴 CsrfFilter 의 저장소를 HttpSession 기반 TestCsrfTokenRepository 로 바꿔치기해 같은 컨텍스트의 다른 테스트에 영향을 준 것. 쿠키 테스트를 `CsrfCookieTest`(고유 속성으로 컨텍스트 분리, csrf() 미사용)로 옮김.

그다음: 나머지 실패 수정 → 사용자 확인 → 커밋. 이후 남은 기능은 F-07(제출·최종 확인·확정·문서함), llm 컨텍스트(F-02·F-06·F-03 AI·F-09·F-10), P2(F-08·F-15·F-16·F-17).
