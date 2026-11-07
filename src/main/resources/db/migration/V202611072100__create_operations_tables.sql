-- 운영 업무 도메인 P1(FUNCTIONAL_SPEC 3-3장): 협의(review)·담당자 배정(assignment)·처리 기한(deadlines)·감사 이력(audit).
-- 외래 키 제약 없음(참조 무결성은 애플리케이션), 기본 키는 <단수형>_id, 같은 이름 컬럼은 같은 형식.
-- 이미 main 에 머지된 마이그레이션 파일은 절대 고치지 않는다. 변경은 새 파일로 추가한다.

-- 1) 협의(분야별). 2단계 진입 시 서류 유형의 reviewFields 마다 1행. 할당 완료 ≠ 협의 완료(불변식 12).
CREATE TABLE reviews
(
    review_id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_request_id BIGINT      NOT NULL,
    field               VARCHAR(20) NOT NULL CHECK (field IN ('INFRA', 'SECURITY')),
    status              VARCHAR(30) NOT NULL CHECK (status IN ('WAITING', 'ALLOCATED', 'IN_PROGRESS', 'SUPPLEMENT_REQUESTED', 'COMPLETED', 'RECHECK')),
    checklist           JSONB,
    opinion             TEXT,
    allocated_by        VARCHAR(50),
    allocated_at        TIMESTAMPTZ,
    completed_by        VARCHAR(50),
    completed_at        TIMESTAMPTZ,
    revision            BIGINT      NOT NULL DEFAULT 0,
    created_by          VARCHAR(50),
    updated_by          VARCHAR(50),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviews_request_field UNIQUE (security_request_id, field)
);
COMMENT ON TABLE reviews IS '분야별 협의. WAITING 검토 대기 / ALLOCATED 할당 완료(INFRA) / IN_PROGRESS 검토 중 / SUPPLEMENT_REQUESTED 보완 요청 / COMPLETED 협의 완료 / RECHECK 재검토. allocated_at 은 INFRA 할당 완료 시각(할당 완료 ≠ 협의 완료)';

-- 2) 분야별 담당자 목록과 기본 담당자(기관 설정). 1차는 seed, 편집 화면은 P2.
CREATE TABLE reviewer_pools
(
    reviewer_pool_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    field            VARCHAR(20) NOT NULL CHECK (field IN ('INFRA', 'SECURITY')),
    member_username  VARCHAR(50) NOT NULL,
    is_default       BOOLEAN     NOT NULL DEFAULT FALSE,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_reviewer_pools_field_member UNIQUE (field, member_username)
);
CREATE UNIQUE INDEX uq_reviewer_pools_default ON reviewer_pools (field) WHERE is_default AND active;
COMMENT ON TABLE reviewer_pools IS '분야별 배정 가능 담당자. 분야당 활성 기본 담당자 1명(자동 배정 대상)';

-- 3) 배정. 신청·분야당 활성 배정 1건(불변식 20). 재배정은 이전 배정을 비활성으로 보존.
CREATE TABLE assignments
(
    assignment_id       BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_request_id BIGINT      NOT NULL,
    field               VARCHAR(20) NOT NULL CHECK (field IN ('INFRA', 'SECURITY')),
    assignee_username   VARCHAR(50) NOT NULL,
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    reason              TEXT,
    assigned_by         VARCHAR(50),
    assigned_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deactivated_at      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_assignments_active ON assignments (security_request_id, field) WHERE active;
CREATE INDEX idx_assignments_assignee ON assignments (assignee_username) WHERE active;
COMMENT ON TABLE assignments IS '담당자 배정. assigned_by NULL 은 자동 배정. 재배정 시 이전 행은 active=false 로 보존';

-- 4) 처리 기한(저장형, 불변식 22·23·31). 임박·지남 컬럼 없음 — 조회 시 계산.
CREATE TABLE deadlines
(
    deadline_id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_request_id BIGINT      NOT NULL,
    field               VARCHAR(20) CHECK (field IN ('INFRA', 'SECURITY')),
    kind                VARCHAR(30) NOT NULL CHECK (kind IN ('REVIEW', 'SUPPLEMENT_RESPONSE', 'FINAL_CONFIRMATION')),
    started_at          TIMESTAMPTZ NOT NULL,
    due_date            DATE        NOT NULL,
    policy_version      INTEGER     NOT NULL,
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    ended_at            TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_deadlines_field_kind CHECK (
        (kind = 'REVIEW' AND field IS NOT NULL) OR (kind <> 'REVIEW' AND field IS NULL)
    )
);
CREATE UNIQUE INDEX uq_deadlines_active ON deadlines (security_request_id, COALESCE(field, ''), kind) WHERE active;
COMMENT ON TABLE deadlines IS '처리 기한. REVIEW 는 분야별, SUPPLEMENT_RESPONSE·FINAL_CONFIRMATION 은 신청 단위. 재산정은 기존 행 종료 + 새 행';

-- 5) 감사 이력(추가 전용, 불변식 24·25). 업무 변경과 같은 트랜잭션에서 기록. 비밀값·원문 저장 금지.
CREATE TABLE audit_events
(
    audit_event_id  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_username  VARCHAR(50),
    actor_role      VARCHAR(20),
    target_type     VARCHAR(50)  NOT NULL,
    target_id       VARCHAR(100) NOT NULL,
    target_revision BIGINT,
    action          VARCHAR(50)  NOT NULL,
    reason          TEXT,
    summary         TEXT,
    request_id      VARCHAR(64)
);
CREATE INDEX idx_audit_events_target ON audit_events (target_type, target_id, occurred_at);
CREATE INDEX idx_audit_events_actor ON audit_events (actor_username, occurred_at);
COMMENT ON TABLE audit_events IS '감사 이력. 수정·삭제 금지(트리거로 차단). 업무 상태의 원천이 아니라 변화의 기록';

-- 추가 전용 보장: 어떤 계정이든 UPDATE·DELETE 를 거부한다(운영 계정 권한 분리와 별개로 이중 방어).
CREATE OR REPLACE FUNCTION audit_events_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events 는 추가 전용입니다 (%)', TG_OP;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_reject_change();

-- 6) seed: 분야별 기본 담당자 = 시연 계정(DemoAccountSeeder 가 기동 시 만드는 infra·security). 전부 합성 데이터.
INSERT INTO reviewer_pools (field, member_username, is_default)
VALUES ('INFRA', 'infra', TRUE),
       ('SECURITY', 'security', TRUE);
