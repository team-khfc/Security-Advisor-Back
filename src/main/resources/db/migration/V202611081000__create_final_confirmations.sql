-- F-07 4단계: 제출본에 대한 분야별 최종 확인(INFRA 선행 → SECURITY). 같은 제출본·분야는 1건(불변식 34).
-- 확정은 security_requests.status = CONFIRMED 로 표현하고 별도 테이블을 두지 않는다. 외래 키 제약은 두지 않는다(팀 결정).
CREATE TABLE final_confirmations
(
    final_confirmation_id BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id         BIGINT      NOT NULL,
    security_request_id   BIGINT      NOT NULL,
    field                 VARCHAR(20) NOT NULL CHECK (field IN ('INFRA', 'SECURITY')),
    opinion               TEXT,
    confirmed_by          VARCHAR(50) NOT NULL,
    confirmed_at          TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_final_confirmations_submission_field UNIQUE (submission_id, field)
);
CREATE INDEX idx_final_confirmations_request ON final_confirmations (security_request_id);
COMMENT ON TABLE final_confirmations IS '제출본 최종 확인(분야별 1건). 두 분야 확인 후 SECURITY 담당자가 확정(PUT /api/submissions/{id}/confirmation)';
