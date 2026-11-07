-- 기관 설정 게시본(configuration) + 사업 건·신청·답변·제출본(request). FUNCTIONAL_SPEC 8-1 스키마 확정 기준.
-- 기본 키는 <테이블 단수형>_id, 같은 이름의 컬럼은 어디서나 같은 형식(README DB 명세).
-- 이미 main 에 머지된 마이그레이션 파일은 절대 고치지 않는다. 변경은 새 파일로 추가한다.

-- 1) 게시본: 1차는 seed 게시본 1개로 운영(편집 화면 F-08 은 P2). 새 게시본은 새 사업 건에만 적용(불변식 8).
CREATE TABLE publications
(
    publication_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    version        INTEGER     NOT NULL UNIQUE,
    field_schema   JSONB       NOT NULL,
    published_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE publications IS '기관 설정 게시본. field_schema 는 질문 항목(저장 키·범위 CASE/REQUEST·적용 서류 유형·형식·필수·작성 역할)';

-- 2) 사업 건: 공통 정보 1회 수집. 상태 컬럼 없음 — 하위 신청에서 계산(불변식 15).
CREATE TABLE business_cases
(
    business_case_id   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    applicant_username VARCHAR(50)  NOT NULL REFERENCES members (username),
    title              VARCHAR(200) NOT NULL,
    summary            TEXT,
    department         VARCHAR(100),
    publication_id     BIGINT       NOT NULL REFERENCES publications (publication_id),
    revision           BIGINT       NOT NULL DEFAULT 0,
    created_by         VARCHAR(50),
    updated_by         VARCHAR(50),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_business_cases_applicant ON business_cases (applicant_username);
COMMENT ON TABLE business_cases IS '사업 건. 서류별 신청 N건을 거느리며 공통 답변·업로드 문서를 가진다. 생성 시점 게시본 고정';

-- 3) 신청: 서류 1건의 생애주기. 단계(1~4)와 상태를 분리 저장, 허용 조합만(불변식 33).
CREATE TABLE security_requests
(
    security_request_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_case_id    BIGINT      NOT NULL REFERENCES business_cases (business_case_id),
    doc_type            VARCHAR(20) NOT NULL CHECK (doc_type IN ('FIREWALL', 'SECURITY_REVIEW')),
    stage               SMALLINT    NOT NULL CHECK (stage BETWEEN 1 AND 4),
    status              VARCHAR(30) NOT NULL,
    revision            BIGINT      NOT NULL DEFAULT 0,
    created_by          VARCHAR(50),
    updated_by          VARCHAR(50),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_security_requests_case_doc_type UNIQUE (business_case_id, doc_type),
    CONSTRAINT ck_security_requests_stage_status CHECK (
        (stage = 1 AND status = 'DRAFTING')
        OR (stage = 2 AND status IN ('IN_REVIEW', 'SUPPLEMENT_REQUESTED'))
        OR (stage = 3 AND status IN ('FINAL_REVIEW', 'SUPPLEMENT_REQUESTED'))
        OR (stage = 4 AND status IN ('SUBMITTED', 'CONFIRMED'))
        OR status IN ('CLOSED', 'WITHDRAWN')
    )
);
CREATE INDEX idx_security_requests_case ON security_requests (business_case_id);
CREATE INDEX idx_security_requests_stage_status ON security_requests (stage, status);
COMMENT ON TABLE security_requests IS '서류별 신청. 4단계·차수 반환·협의·제출은 신청 단위. 종결(CLOSED)·철회(WITHDRAWN)는 종료 상태';
COMMENT ON COLUMN security_requests.status IS 'DRAFTING 작성 중 / IN_REVIEW 협의 중 / SUPPLEMENT_REQUESTED 보완 요청 / FINAL_REVIEW 최종 검토 중 / SUBMITTED 제출됨 / CONFIRMED 확정 / CLOSED 종결(반려) / WITHDRAWN 철회';

-- 4) 답변: 범위에 따라 사업 건(CASE) 또는 신청(REQUEST)에 속한다. 모든 값은 출처를 가지며, 출처 없음 = 담당자 확인 필요.
CREATE TABLE answers
(
    answer_id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope               VARCHAR(10)  NOT NULL CHECK (scope IN ('CASE', 'REQUEST')),
    business_case_id    BIGINT       NOT NULL REFERENCES business_cases (business_case_id),
    security_request_id BIGINT       REFERENCES security_requests (security_request_id),
    field_key           VARCHAR(100) NOT NULL,
    value               TEXT,
    value_source        VARCHAR(20)  CHECK (value_source IN ('USER_ANSWER', 'AI_INFERRED', 'STAFF_INPUT', 'STAFF_CONFIRMED')),
    evidence            TEXT,
    confirmed           BOOLEAN      NOT NULL DEFAULT FALSE,
    confirmed_by        VARCHAR(50),
    confirmed_at        TIMESTAMPTZ,
    needs_review        BOOLEAN      NOT NULL DEFAULT FALSE,
    revision            BIGINT       NOT NULL DEFAULT 0,
    created_by          VARCHAR(50),
    updated_by          VARCHAR(50),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_answers_scope_owner CHECK (
        (scope = 'CASE' AND security_request_id IS NULL)
        OR (scope = 'REQUEST' AND security_request_id IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uq_answers_case_field ON answers (business_case_id, field_key) WHERE scope = 'CASE';
CREATE UNIQUE INDEX uq_answers_request_field ON answers (security_request_id, field_key) WHERE scope = 'REQUEST';
COMMENT ON TABLE answers IS '인터뷰 답변(필드 단위). value_source NULL = 담당자 확인 필요. AI_INFERRED 는 confirmed 전까지 미확정(불변식 1)';
COMMENT ON COLUMN answers.evidence IS 'AI 추론 근거(원문 위치·요약). AI_INFERRED 이면 필수';
COMMENT ON COLUMN answers.needs_review IS '앞선 답변 변경·차수 반환으로 재검토가 필요한 항목';

-- 5) 제출본: 제출 시점의 불변 스냅샷(공통 값 + 서류별 값 + 설정 버전). 멱등성 키로 중복 제출 차단(불변식 4).
CREATE TABLE submissions
(
    submission_id       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_request_id BIGINT       NOT NULL REFERENCES security_requests (security_request_id),
    publication_id      BIGINT       NOT NULL REFERENCES publications (publication_id),
    sequence_no         INTEGER      NOT NULL,
    snapshot            JSONB        NOT NULL,
    idempotency_key     VARCHAR(100) NOT NULL UNIQUE,
    submitted_by        VARCHAR(50)  NOT NULL REFERENCES members (username),
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_submissions_request_sequence UNIQUE (security_request_id, sequence_no)
);
COMMENT ON TABLE submissions IS '제출본. 생성 후 불변 — 보완은 새 개정본(sequence_no 증가)';

-- 6) seed 게시본 v1: 서류 2종 최소 필드 스키마(기획 확정 전 임시, 전부 합성). 기획(안대웅) 확정 시 새 게시본으로 교체한다.
INSERT INTO publications (version, field_schema)
VALUES (1, '{
  "fields": [
    {"key": "project.name", "label": "사업(서비스)명", "scope": "CASE", "docTypes": ["FIREWALL", "SECURITY_REVIEW"], "type": "TEXT", "required": true, "askRole": "APPLICANT", "hint": "기관 내부에서 부르는 사업 또는 서비스 이름"},
    {"key": "project.summary", "label": "사업 개요", "scope": "CASE", "docTypes": ["FIREWALL", "SECURITY_REVIEW"], "type": "LONG_TEXT", "required": true, "askRole": "APPLICANT", "hint": "무엇을 위해 어떤 시스템을 쓰는지 2~3문장"},
    {"key": "project.department", "label": "주관 부서", "scope": "CASE", "docTypes": ["FIREWALL", "SECURITY_REVIEW"], "type": "TEXT", "required": true, "askRole": "APPLICANT", "hint": null},
    {"key": "project.period", "label": "사업·서비스 기간", "scope": "CASE", "docTypes": ["FIREWALL", "SECURITY_REVIEW"], "type": "DATE_RANGE", "required": true, "askRole": "APPLICANT", "hint": "시작일과 종료일. 상시 운영이면 종료일 없음"},
    {"key": "data.personal_info", "label": "개인정보 처리 여부", "scope": "CASE", "docTypes": ["FIREWALL", "SECURITY_REVIEW"], "type": "BOOLEAN", "required": true, "askRole": "APPLICANT", "hint": "이름·주민번호·연락처 등 개인정보를 저장·전송하는지"},
    {"key": "system.external_transfer", "label": "외부(인터넷) 전송 여부", "scope": "CASE", "docTypes": ["FIREWALL", "SECURITY_REVIEW"], "type": "BOOLEAN", "required": true, "askRole": "APPLICANT", "hint": "기관 밖 시스템과 데이터를 주고받는지"},
    {"key": "system.target_server", "label": "대상 서버(시스템명)", "scope": "CASE", "docTypes": ["FIREWALL", "SECURITY_REVIEW"], "type": "TEXT", "required": true, "askRole": "INFRA", "hint": "ICT인프라 담당자가 할당·확인하는 항목. 신청자는 모르면 비워 둔다"},
    {"key": "network.source", "label": "출발지 IP/대역", "scope": "CASE", "docTypes": ["FIREWALL", "SECURITY_REVIEW"], "type": "TEXT", "required": true, "askRole": "INFRA", "hint": "ICT인프라 담당자가 할당한다"},
    {"key": "network.destination", "label": "목적지 IP", "scope": "CASE", "docTypes": ["FIREWALL", "SECURITY_REVIEW"], "type": "TEXT", "required": true, "askRole": "INFRA", "hint": "ICT인프라 담당자가 할당한다"},
    {"key": "firewall.protocol", "label": "프로토콜", "scope": "REQUEST", "docTypes": ["FIREWALL"], "type": "TEXT", "required": true, "askRole": "APPLICANT", "hint": "예: TCP, UDP"},
    {"key": "firewall.port", "label": "포트", "scope": "REQUEST", "docTypes": ["FIREWALL"], "type": "TEXT", "required": true, "askRole": "APPLICANT", "hint": "예: 443"},
    {"key": "firewall.period", "label": "허용 기간", "scope": "REQUEST", "docTypes": ["FIREWALL"], "type": "DATE_RANGE", "required": true, "askRole": "APPLICANT", "hint": "종료일 없는 상시 규칙은 정기 재검토일이 필요하다"},
    {"key": "firewall.purpose", "label": "허용 목적", "scope": "REQUEST", "docTypes": ["FIREWALL"], "type": "LONG_TEXT", "required": true, "askRole": "APPLICANT", "hint": "어떤 업무를 위해 통신이 필요한지"},
    {"key": "review.access_control", "label": "접근 권한 통제 방식", "scope": "REQUEST", "docTypes": ["SECURITY_REVIEW"], "type": "LONG_TEXT", "required": true, "askRole": "APPLICANT", "hint": "누가 어떤 권한으로 접근하는지"},
    {"key": "review.logging", "label": "접속·처리 기록 보관", "scope": "REQUEST", "docTypes": ["SECURITY_REVIEW"], "type": "LONG_TEXT", "required": true, "askRole": "APPLICANT", "hint": "로그 항목과 보관 기간"},
    {"key": "review.encryption", "label": "중요정보 암호화", "scope": "REQUEST", "docTypes": ["SECURITY_REVIEW"], "type": "LONG_TEXT", "required": false, "askRole": "APPLICANT", "hint": "저장·전송 구간 암호화 방식"}
  ]
}'::jsonb);
