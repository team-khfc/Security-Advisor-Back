# Security-Advisor-Back

보안길잡이(SecurityAdvisor) 백엔드 — Spring Boot 4.1 · Java 25 · PostgreSQL 17 · Flyway · Gradle(Kotlin DSL).
규칙·구조·명령은 팀 Claude 프로젝트의 AGENTS.md를 따른다(저장소 루트 사본은 추후 추가).

## 준비물

- JDK 25 (없으면 Gradle toolchain이 자동으로 내려받는다)
- Docker (개발용 DB와 테스트의 Testcontainers에 필요)
- PostgreSQL 17 — `jdbc:postgresql://localhost:5432/boan` (계정 `boan`/`boan`, `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`로 변경)

## 실행

```bash
./gradlew bootRun            # http://localhost:8080 , Swagger UI: /swagger-ui.html
./gradlew spotlessApply check  # 커밋 전 필수: 포맷 + 테스트 + ArchUnit
```

## 현재 구현 범위

- `auth`: 시연용 세션 로그인(`POST /api/auth/login`, `/logout`, `GET /api/auth/me`), 역할 4종, 시연 계정 seed
- `procedure`: 예제 기능(절차 목록 조회)
- `common`: ProblemDetail 오류 처리, Spring Security 설정, JPA Auditing, ArchUnit 패키지 규칙

시연 계정·비밀번호는 합성 데이터이며 운영 값이 아니다. 비밀값은 환경변수로만 받는다.

## DB 명세

스키마는 Flyway 마이그레이션(`src/main/resources/db/migration/`)으로만 바꾼다. JPA는 `ddl-auto: validate`로 엔티티와 테이블이 맞는지만 확인한다. main에 머지된 마이그레이션 파일은 수정하지 않고 새 파일(`V<연월일시분>__<설명>.sql`)을 추가한다. 테이블·컬럼은 snake_case, 테이블명은 복수형이다. 운영 기관은 설치당 1곳 고정이라 기관 테이블과 기관 키 컬럼은 없다(FUNCTIONAL_SPEC.md 3-4장).

### ERD — 현재 (마이그레이션 2건)

```mermaid
erDiagram
    members {
        varchar(50)  username PK "로그인 아이디"
        varchar(100) password_hash "bcrypt 해시"
        varchar(100) display_name
        varchar(20)  role "APPLICANT | SECURITY | INFRA | ADMIN"
        boolean      active "false면 로그인 불가"
        timestamptz  created_at
        timestamptz  updated_at
    }
    procedures {
        bigint       id PK "IDENTITY"
        varchar(50)  code UK "SECURITY_REVIEW 등"
        varchar(100) name
        varchar(500) description
        boolean      active "false면 목록 숨김"
        timestamptz  created_at
        timestamptz  updated_at
    }
```

### ERD — 예정 (FUNCTIONAL_SPEC.md 8-1 스키마 확정 기준, 컬럼은 대표 항목만)

```mermaid
erDiagram
    members ||--o{ business_cases : "신청자"
    members ||--o{ assignments : "담당자"
    members ||--o{ audit_events : "행위자"
    members ||--o{ notifications : "수신자"
    publications ||--o{ business_cases : "생성 시점 게시본 고정"
    configurations ||--o{ publications : "게시"
    business_cases ||--|{ security_requests : "1 : N (doc_type 유일)"
    business_cases ||--o{ answers : "scope=CASE"
    business_cases ||--o{ uploaded_files : ""
    business_cases ||--o{ analysis_jobs : ""
    security_requests ||--o{ answers : "scope=REQUEST"
    security_requests ||--o{ reviews : "분야별 INFRA/SECURITY"
    security_requests ||--o{ supplements : ""
    security_requests ||--o{ stage_returns : "사유 필수"
    security_requests ||--o{ submissions : "불변 스냅샷"
    security_requests ||--o{ assignments : "분야당 활성 1건"
    security_requests ||--o{ deadlines : "저장형 기한일"
    security_requests ||--o{ generated_documents : ""
    submissions ||--o{ final_confirmations : "분야별 1건"
    submissions ||--o{ firewall_rules : "FIREWALL 확정 시 생성"
    reviewer_pools }o--|| members : "분야별 담당자"

    business_cases {
        bigint id PK
        varchar applicant_username FK
        bigint  publication_id FK "상태 컬럼 없음(계산값)"
    }
    security_requests {
        bigint  id PK
        bigint  business_case_id FK
        varchar doc_type "FIREWALL | SECURITY_REVIEW"
        smallint stage "1~4"
        varchar status "단계x상태 CHECK"
        bigint  revision
    }
    answers {
        bigint  id PK
        varchar scope "CASE | REQUEST"
        varchar field_key
        varchar value_source "USER_ANSWER | AI_INFERRED | STAFF_INPUT | STAFF_CONFIRMED"
        bigint  revision
    }
    reviews {
        bigint  id PK
        varchar field "INFRA | SECURITY"
        varchar status "검토대기 | 할당완료 | 검토중 | 보완요청 | 협의완료 | 재검토"
    }
    submissions {
        bigint  id PK
        jsonb   snapshot "공통값+서류별값+설정버전"
        varchar idempotency_key UK
    }
    assignments {
        bigint  id PK
        varchar field
        varchar assignee_username FK
        boolean active
        varchar reason
    }
    deadlines {
        bigint  id PK
        varchar kind "협의 | 보완응답 | 최종확정"
        date    due_date "저장형"
        boolean active
    }
    audit_events {
        bigint  id PK "추가 전용"
        varchar actor_username FK
        varchar target_type
        bigint  target_id
        varchar action
        varchar reason
    }
    firewall_rules {
        bigint  id PK
        bigint  submission_id FK
        varchar status "적용대기 | 적용완료 | 적용불가 | 만료예정 | 만료 | 회수"
        date    end_date
        date    review_date "end_date 또는 review_date 필수"
    }
    llm_configurations {
        bigint  id PK
        varchar provider
        varchar base_url "허용 목록"
        varchar api_key_env "환경변수 이름만"
    }
```

### 공통 컬럼

모든 테이블은 `BaseTimeEntity`의 `created_at`·`updated_at`(TIMESTAMPTZ, JPA Auditing이 자동 기록)을 가진다. 담당자·시각을 남겨야 하는 업무 테이블은 `BaseAuditEntity`로 `created_by`·`updated_by`(VARCHAR(50), 로그인 username)를 추가한다.

### members — 사용자 계정 (`V202611070900__create_members.sql`)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| username | VARCHAR(50) | PK | 로그인 아이디. 다른 테이블의 `created_by` 등이 이 값으로 사용자를 참조 |
| password_hash | VARCHAR(100) | NOT NULL | bcrypt 해시(`{bcrypt}$2b$…`, DelegatingPasswordEncoder 형식). 평문 저장 금지 |
| display_name | VARCHAR(100) | NOT NULL | 화면 표시 이름 |
| role | VARCHAR(20) | NOT NULL, CHECK IN (APPLICANT, SECURITY, INFRA, ADMIN) | 역할 4종. 계정당 1개 |
| active | BOOLEAN | NOT NULL DEFAULT TRUE | false면 로그인 불가. 이력이 참조하므로 삭제 대신 비활성화 |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | 공통 컬럼 |

seed: 시연 계정 4건(`applicant`·`security`·`infra`·`admin`, 역할별 1건) — 전부 합성 데이터. 비밀번호를 환경변수로 받아 해시하는 방식(F-11)과 부서·가입 경로·로그인 실패 횟수·잠금 시각 컬럼, 신청자 2번째 계정은 후속 마이그레이션으로 추가 예정.

### procedures — 보안 절차 종류 (`V202611041300__create_procedures.sql`, 예제 기능)

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK, IDENTITY | 내부 식별자 |
| code | VARCHAR(50) | NOT NULL, UNIQUE | 절차 고정 코드. API·화면에서 id 대신 사용 |
| name | VARCHAR(100) | NOT NULL | 표시 이름 |
| description | VARCHAR(500) | | 설명 |
| active | BOOLEAN | NOT NULL DEFAULT TRUE | false면 목록에서 숨김(삭제하지 않음) |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | 공통 컬럼 |

seed: `SECURITY_REVIEW`·`FIREWALL_REQUEST`·`USB_REQUEST` 3건. 이 테이블은 패키지 구조 예제이며, 1차 범위의 서류 유형은 FUNCTIONAL_SPEC.md의 `DocType`(FIREWALL / SECURITY_REVIEW)으로 정리될 예정이다(USB는 범위 밖).

### 예정 테이블

FUNCTIONAL_SPEC.md 8-1 스키마 확정 체크리스트 기준으로 `business_cases`·`security_requests`·`answers`·`uploaded_files`·`analysis_jobs`·`submissions`·`reviews`·`supplements`·`stage_returns`·`final_confirmations`·`generated_documents`·`configurations`·`publications`·`reviewer_pools`·`assignments`·`deadlines`·`audit_events`·`firewall_rules`·`notifications`·`knowledge_sources`·`private_contexts`·`llm_configurations`를 마이그레이션으로 추가한다. 이 README의 표는 마이그레이션이 늘 때마다 같은 PR에서 갱신한다.
