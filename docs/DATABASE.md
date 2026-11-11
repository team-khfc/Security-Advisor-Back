# DB 명세

명세와 의도적으로 다른 결정은 [ARCHITECTURE.md](ARCHITECTURE.md) 5장, 기술 결정은 4장에 있다.

스키마는 Flyway 마이그레이션(`src/main/resources/db/migration/`)으로만 바꾼다. JPA는 `ddl-auto: validate`로 엔티티와 테이블이 맞는지만 확인한다. main에 머지된 마이그레이션 파일은 수정하지 않고 새 파일(`V<연월일시분>__<설명>.sql`)을 추가한다. **외래 키 제약(FOREIGN KEY)은 두지 않는다**(팀 결정 2026-11-07, 성능·운영 단순화). 참조 무결성은 도메인·서비스가 지키고, ERD의 관계선은 논리적 참조를 뜻한다. 참조 컬럼에는 조회용 인덱스를 둔다. 업무 규칙을 담은 CHECK·UNIQUE(단계×상태 조합, 사업 건당 서류 유형별 진행 중 신청 1건(종료 상태 제외), 답변 유일, 멱등성 키 등)는 유지한다. 테이블·컬럼은 snake_case, 테이블명은 복수형이다. **기본 키 컬럼 이름은 맨 `id`를 쓰지 않고 `<테이블 단수형>_id`로 짓는다**(예: `business_cases.business_case_id`, `reviews.review_id`). 이렇게 하면 다른 테이블의 외래 키(`security_requests.business_case_id`)와 이름·형식이 같아지며, **같은 이름의 컬럼은 모든 테이블에서 같은 의미·같은 데이터 형식**이어야 한다(예: `business_case_id`는 어디서나 BIGINT, `created_by`는 어디서나 VARCHAR(50) username). `members`의 기본 키는 로그인 아이디 `username`(VARCHAR(50))이고, 이를 참조하는 컬럼도 같은 형식에 `_username` 접미사를 쓴다(`applicant_username`, `created_by`는 예외로 유지). 운영 기관은 설치당 1곳 고정이라 기관 테이블과 기관 키 컬럼은 없다([FUNCTIONAL_SPEC.md](../FUNCTIONAL_SPEC.md) 3-4장).

## ERD (마이그레이션 33건, 테이블 35개)

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

## 공통 컬럼

대부분의 테이블은 `BaseTimeEntity` 의 `created_at`·`updated_at`(TIMESTAMPTZ, JPA Auditing 이 자동 기록)을 가진다. 담당자를 남겨야 하는 업무 테이블은 `BaseAuditEntity` 로 `created_by`·`updated_by`(VARCHAR(50), 로그인 username)를 더한다. 변경 가능한 애그리거트는 낙관적 잠금용 `revision` 을 가진다.
