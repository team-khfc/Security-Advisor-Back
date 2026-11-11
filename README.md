# 보안길잡이 · 백엔드

**사업 기획부터 보안 서류 확정까지, 담당자 협의를 연결하는 업무 서버입니다.**

보안길잡이(SecurityAdvisor)의 인증·권한, 신청·협의, AI 연동, 문서 생성, 이력과 관리자 기능을 제공합니다. 이 저장소는 **백엔드 개발 원본과 OpenAPI 계약**을 관리합니다.

> **심사·전체 서비스 실행:** 화면·DB·테스트 계정까지 포함한 [통합 배포 저장소](https://github.com/team-khfc/Security-Advisor)를 이용하세요. 여기서는 백엔드 개발과 단독 검증 방법을 안내합니다.

## 주요 기능

| 영역 | 구현 내용 |
|---|---|
| 사업·신청 | 사업 1건에 여러 서류, 공통·서류별 답변, 항목별 작성·확인 권한과 형식 검증 |
| 협의 | 담당자 배정·인계, 서버·망·운영 정보, 보완·반환·재협의, 변경 이력 |
| AI | 기획서 분석, 인터뷰·답변 제안, 담당자 질의, 서버·망 제안, 협의 요약·최종 초안 |
| 문서 | 신청자 최종 확인·제출, 불변 제출본, 분야별 최종 확인, 서류 확정·PDF |
| 운영 | 방화벽 적용·적용 불가·회수·연장 기록, 알림·처리할 일·기한 |
| 관리자 | 질문·기한 정책 및 지식 게시, 버전 유지, 동시 수정 충돌, 업무 지표, AI 모델 설정·시험·복원 |
| 접근 제어 | 본인·배정 담당자 중심 조회, 역할 변경·비활성화 시 세션 재검증, 감사 이력 |

지원 서류: **방화벽 정책 허용 신청서**, **정보화사업 보안성검토 신청서**. 서류 확정과 실제 방화벽 장비 적용은 별개이며 이 서버는 실제 장비 설정을 변경하지 않습니다.

## 빠른 시작 — Docker

Docker Desktop 또는 Docker Engine + Compose v2가 필요합니다. 저장소 루트에서 실행합니다.

```bash
cp .env.example .env
docker compose up -d --build
```

Windows PowerShell에서는 첫 명령 대신 `Copy-Item .env.example .env`를 사용합니다. 기존 `.env`는 덮어쓰지 않습니다. 외부 모델 없이 업무 API를 확인하려면 `.env`의 `LLM_MODE=fake`로 설정합니다.

| 주소 | 용도 |
|---|---|
| [localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | API 탐색·시험 |
| [localhost:8080/actuator/health](http://localhost:8080/actuator/health) | 서버 상태 |

기본 계정은 `applicant`, `applicant2`, `infra`, `security`, `admin`입니다. 최초 생성 비밀번호는 `.env`의 `DEMO_ACCOUNT_PASSWORD`이며, 비어 있으면 계정을 생성하지 않습니다. 통합 심사용 배포의 지정 계정·시나리오와는 별도입니다.

```bash
docker compose stop          # 데이터 유지, 중지
docker compose up -d         # 기존 데이터로 재실행
```

## 로컬 Java 개발

준비물: **JDK 25**, 접근 가능한 PostgreSQL 17, Docker(통합 검사), Python 3(문서 검사). Gradle은 저장소의 Wrapper를 사용합니다.

```bash
./gradlew bootRun
```

Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다. 로컬 실행은 `.env`의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 접근할 수 있는 PostgreSQL이 필요합니다. Docker DB를 PC에서 직접 사용할 경우 로컬 DB 포트 공개 설정을 별도로 확인하세요. 전체 컨테이너 실행과 로컬 실행을 같은 포트로 동시에 시작하지 않습니다.

## 환경변수와 실제 AI

전체 항목과 기본값은 [.env.example](.env.example)을 확인하세요. 기관은 설치당 한 곳이며 환경변수로 설정합니다.

| 변수 | 용도 |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 로컬 서버의 DB 연결 |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Compose DB·앱 연결 |
| `INSTITUTION_NAME`, `INSTITUTION_CODE` | 운영 기관 표시 |
| `DEMO_ACCOUNT_PASSWORD` | 최초 시연 계정 생성 비밀번호 |
| `STORAGE_DIR` | 업로드·문서 저장 위치 |
| `LLM_MODE` | `fake`: 고정 모의 응답 / `live`: 실제 모델 호출 |
| `OPENAI_API_KEY`, `OPENROUTER_API_KEY`, `OLLAMA_API_KEY` | 공급자별 키 |
| `LLM_ALLOWED_HOSTS`, `LLM_ALLOWED_KEY_ENVS` | 모델 서버·키 참조 허용 목록 |

실제 AI는 `LLM_MODE=live`와 공급자 키를 설정한 후 앱을 재생성해 사용합니다.

```bash
docker compose up -d --no-deps app
```

관리 API `/api/llm-configurations` 또는 프론트의 **AI 모델 구성** 화면에서 모델·주소·용도·최대 토큰·우선순위를 설정합니다. 키 값은 DB에 저장하지 않고 환경변수 이름만 참조합니다. 활성 상태·키·허용 호스트 조건을 만족하는 구성 중 **작은 우선순위 값부터** 선택합니다. 연결 시험 성공만으로 기본 모델이 변경되지는 않습니다.

용도는 `INTERVIEW`, `SUMMARY`, `DOCUMENT`, `INQUIRY`입니다. 협의 요약·최종 초안은 `DOCUMENT`를 사용합니다. 실제 검증은 GPT 협의 요약 4096토큰, DeepSeek 문서용 8192토큰으로 수행했습니다. 출력 한도로 잘린 응답과 잘못된 근거는 저장하지 않으며 AI 결과는 사람의 확인을 거칩니다. 공급자 호출 비용과 모델 사용 권한은 실행 환경에 따라 달라집니다.

## API와 업무 흐름

**작성 → ICT인프라·정보보안 협의 → 신청자 최종 검토·제출 → 담당자 최종 확인·확정**

| API 영역 | 주요 경로 |
|---|---|
| 사업·신청 | `/api/business-cases`, `/api/security-requests` |
| 협의·구성 | `/api/reviews`, 신청별 `/infrastructure-plan` |
| 협의 비교·요약 | 신청별 `/consultation-comparison`, `/consultation-summaries` |
| AI·문서 | 사업별 `/interview-turns`, `/api/analysis-jobs`, `/api/generated-documents` |
| 모델 관리 | `/api/llm-configurations`, `/api/llm-connection-tests` |

요청·응답·권한·상태 코드는 [OpenAPI 계약](docs/api/openapi.yaml)을 기준으로 확인하세요. [계약 갱신 방법](docs/api/README.md), [프론트 연동 안내](docs/FRONTEND_HANDOFF.md)도 제공합니다.

## 검사

```bash
python3 tools/check_doc_versions.py
./gradlew check
```

`check`는 형식, 컴파일, 테스트, ArchUnit 계층 규칙과 API 계약 일치를 검사합니다. DB 통합 검사는 Testcontainers를 사용하므로 Docker가 실행 중이어야 합니다. 형식 수정은 `./gradlew spotlessApply`입니다. 키 없이 모의 모델로 실행하는 자동 검사와 실제 모델 검증은 구분합니다.

## 기술과 구조

Java 25 · Spring Boot 4.1 · Spring AI 2 · PostgreSQL 17 · Flyway · Gradle Kotlin DSL

```text
src/main/java/kr/securityadvisor/
  auth/ common/                 인증·보안·공통 기반
  request/ review/ assignment/   신청·협의·담당자 배정
  llm/ knowledge/ document/     AI·지식·문서
  configuration/ policy/       설정·방화벽 정책
  notification/ audit/ insight/ 알림·감사·지표
src/main/resources/db/migration/ 데이터베이스 변경 이력
src/test/                       단위·API·업무 흐름 검사
docs/api/                       OpenAPI 계약
```

기능별로 controller → service → domain/repository를 구분하고, 업무 규칙을 도메인에 둡니다. 최신 테이블·제약은 마이그레이션이 기준입니다.

## 참고 문서

- [업무 명세](FUNCTIONAL_SPEC.md) · [개발 규칙](AGENTS.md) · [기여 안내](CONTRIBUTING.md)
- [구조](docs/ARCHITECTURE.md) · [설계 결정](docs/adr/) · [시큐어코딩](SECURE_CODING.md)
- [GPT 근거 검증](docs/GPT_SUMMARY_EVIDENCE_20261111.md) · [출력 한도 처리](docs/CONSULTATION_SUMMARY_LIMIT_20261111.md)
- [통합 배포](https://github.com/team-khfc/Security-Advisor) · [프론트 원본](https://github.com/team-khfc/Security-Advisor-Front)

개별 검증 기록은 작성 시점의 결과입니다. 제출 버전의 출처와 통합 검증은 배포 저장소의 `RELEASE.md`와 `docs/JUDGE_VALIDATION.md`를 확인하세요.

## 메일 알림의 암호화

메일 알림은 SMTP_HOST와 발신 주소가 있을 때만 활성화됩니다. `SMTP_STARTTLS=true`는 STARTTLS를 반드시 지원하는 서버(보통 587번), `false`는 처음부터 TLS로 연결하는 서버(보통 465번)를 사용합니다. 평문 릴레이는 지원하지 않으며 인증서 신뢰와 서버 이름을 검증합니다. 사설 인증서 환경에서는 JVM 신뢰 저장소를 구성해야 합니다.
