# Security-Advisor-Back — AI 에이전트 작업 메모

보안길잡이(SecurityAdvisor) 백엔드. **처음이면 [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) 5장의 공식 기준 버전 표부터 읽고** 참조 문서의 머리말 버전·개정일을 확인한다.
그다음 [claude/INSTRUCTIONS.md](claude/INSTRUCTIONS.md)의 참조 순서에 따라 FUNCTIONAL_SPEC.md·AGENTS.md 등 원본 규칙을 읽고, `python3 tools/check_doc_versions.py`로 버전·날짜·경로를 확인한다.
규칙 확인 후 `docs/ARCHITECTURE.md`(컨텍스트 지도·주요 흐름), `README.md`(API 표·ERD·실행), `docs/adr/`(결정 이유), `docs/DEVIATIONS.md`(명세와 다른 점), `docs/api/openapi.yaml`(생성된 API 계약)을 읽는다.
문서 동기화 출처는 `docs/SOURCE_PROVENANCE.md`에 있다. 기존 ADR·DEVIATIONS 기록을 보존하고, 원본 규칙과 구현이 다른 항목은 차이를 먼저 보고한 뒤 PROJECT_RULES.md 7-1의 개정 절차로 정리한다.

## 반드시 지킬 것

- **커밋·push 는 사용자가 "커밋해"라고 할 때만** 한다. 단계마다 커밋하라는 지시도 그 요청에만 적용된다. 자동 커밋 금지.
- 실제 내부 정보(내부망 IP·서버명·자산·실존 개인정보) 금지 — 코드·테스트·문서 전부 합성 데이터.
- 비밀값은 환경변수만(`.env` 는 git 제외). 코드·DB 평문 저장 금지. 사용자가 키를 채팅에 붙이게 하지 않는다.
- 실행하지 않은 검사를 통과로 보고하지 않는다. 미검증은 미검증이라고 쓴다.
- DDD 전술 패턴: `kr.securityadvisor.<컨텍스트>/{controller,dto,service,domain,repository}`. 상태 전이는 도메인 메서드(setter 금지), 컨텍스트 간 연결은 ID + 공개 서비스(+ 같은 트랜잭션의 동기 ApplicationEvent). 순환 금지(ArchUnit).
- RESTful: `/api/<복수형-kebab>`, 상태 전이는 하위 자원 POST / 단일 하위 자원 PUT, 오류는 ProblemDetail(400/401/403/404/409/422/503).
- DB: Flyway 마이그레이션만(`V<연월일시분>__desc.sql`, 머지된 파일 수정 금지), FK 제약 없음, PK 는 `<단수형>_id`. JPA 는 `ddl-auto: validate`.
- Lombok 은 허용 목록만: `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)`, `@Slf4j` (`lombok.config` 가 나머지를 막는다).
- 코드 스타일은 Spotless(palantir) — `gradlew spotlessApply` 로 맞춘다. 줄 끝은 LF(`.gitattributes`).
- 미구현은 미구현으로 보이게 한다(예: `GET /api/document-export-formats` 의 `implemented=false`).

## 검사 명령

- Docker 가 필요한 테스트(Testcontainers)는 `@Tag("docker")`. Docker 없는 환경에서는 `-PexcludeTags=docker` 로 제외한다.
- 다른 환경에서 같은 소스 트리를 빌드할 때는 `-PbuildDirOverride=<경로>` 로 출력 폴더를 분리한다(PC 의 build/ 결과 보존).
- `.tools/jdk-25`(Linux JDK)·`.gradle-home`(Gradle 캐시)은 오프라인 VM 빌드용 로컬 자원이며 git 제외.
- 실제 모델 호출 검증은 `OPENROUTER_API_KEY` 환경변수가 있을 때만 `AiProposalLiveTest` 가 돈다.

```
gradlew spotlessApply check -PexportOpenApi=true   # 포맷 + 컴파일 + 테스트(Docker) + ArchUnit + openapi.yaml 재생성
gradlew test --tests "kr.securityadvisor.auth.AuthControllerTest"
```

## 현재 상태 (2026-11-08, 커밋 e18e57d)

- 기능 F-01~F-17 의 1차 범위(P1·P2) 전부 + P3 일부(서류 추가·AI 유형 제안·규칙 연장·기한 정지·메일/웹훅 채널·HWP 추출) 구현. 제출 준비물 중 완료: `compose.yaml`, CI, ADR 0001~0007, OpenAPI 계약.
- 남은 것(범위 밖·후속): `docs/ARCHITECTURE.md` 6장 목록. 제출 준비물: 규칙 문서 사본 복사, 배포 저장소 Security-Advisor(Back+Front compose·구동안내서).
- 빌드 환경: PC(Windows, Docker Desktop)에서 `check` 를 돌리고 `build/gradle-console.log`(UTF-16LE)·`build/test-results` 로 결과를 읽는 방식으로 협업했다.
