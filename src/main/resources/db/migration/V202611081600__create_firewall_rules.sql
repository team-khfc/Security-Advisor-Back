-- F-15 방화벽 규칙 수명주기: FIREWALL 서류 확정 시 제출본 값에서 규칙 단위로 생성(불변식 26), 적용·불가·회수는 INFRA 의 기록으로만(불변식 27).
-- 종료일 없는 규칙은 정기 재검토일이 반드시 있다(불변식 28). 만료 예정·만료는 조회 시 계산한다. 외래 키 제약은 두지 않는다(팀 결정).
CREATE TABLE firewall_rules
(
    firewall_rule_id    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_request_id BIGINT       NOT NULL,
    submission_id       BIGINT       NOT NULL,
    rule_no             INTEGER      NOT NULL,
    source              VARCHAR(200) NOT NULL,
    destination         VARCHAR(200) NOT NULL,
    protocol            VARCHAR(20)  NOT NULL,
    port                VARCHAR(50)  NOT NULL,
    start_date          DATE         NOT NULL,
    end_date            DATE,
    review_date         DATE,
    status              VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING_APPLY', 'APPLIED', 'NOT_APPLICABLE', 'REVOKED')),
    applied_by          VARCHAR(50),
    applied_at          TIMESTAMPTZ,
    application_note    TEXT,
    reason              TEXT,
    revision            BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_firewall_rules_end_or_review CHECK (end_date IS NOT NULL OR review_date IS NOT NULL),
    CONSTRAINT uq_firewall_rules_submission_no UNIQUE (submission_id, rule_no)
);
CREATE INDEX idx_firewall_rules_request ON firewall_rules (security_request_id);
CREATE INDEX idx_firewall_rules_status ON firewall_rules (status, end_date);
COMMENT ON TABLE firewall_rules IS '방화벽 규칙 한 줄. 값은 확정 제출본에서 복사되고 수정 불가(바꾸려면 새 신청). 서류 확정 ≠ 적용';
COMMENT ON COLUMN firewall_rules.status IS '저장 상태 4종. 만료 예정(EXPIRING)·만료(EXPIRED)는 APPLIED 규칙의 end_date 로 조회 시 계산';
