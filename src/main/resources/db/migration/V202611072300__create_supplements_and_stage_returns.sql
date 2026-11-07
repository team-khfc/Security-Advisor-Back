-- 보완 요청(supplements)과 차수 반환(stage_returns) — FUNCTIONAL_SPEC F-05, 8-1. 외래 키 없음, 기본 키 <단수형>_id.
-- 이미 main 에 머지된 마이그레이션 파일은 절대 고치지 않는다. 변경은 새 파일로 추가한다.

-- 1) 보완 요청: 담당자가 대상 항목·사유를 명시해 신청자에게 요청. 신청자가 답하면 담당자 재검토로 복귀, 협의 완료 시 해소.
CREATE TABLE supplements
(
    supplement_id       BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_request_id BIGINT      NOT NULL,
    review_id           BIGINT      NOT NULL,
    field               VARCHAR(20) NOT NULL CHECK (field IN ('INFRA', 'SECURITY')),
    target_field_keys   JSONB       NOT NULL,
    reason              TEXT        NOT NULL,
    status              VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'ANSWERED', 'RESOLVED')),
    requested_by        VARCHAR(50) NOT NULL,
    requested_at        TIMESTAMPTZ NOT NULL,
    answer_note         TEXT,
    answered_at         TIMESTAMPTZ,
    resolved_by         VARCHAR(50),
    resolved_at         TIMESTAMPTZ,
    revision            BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_supplements_request ON supplements (security_request_id, status);
COMMENT ON TABLE supplements IS '보완 요청. OPEN 미해결 → ANSWERED 신청자 답변 → RESOLVED 담당자 재검토 후 해소. 제출 조건: OPEN 없음(불변식 2)';

-- 2) 차수 반환 기록(불변식 11): 사유 필수, 지정 항목 목록. 값은 지우지 않으며 지정 항목만 재검토 표시한다.
CREATE TABLE stage_returns
(
    stage_return_id     BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_request_id BIGINT      NOT NULL,
    from_stage          SMALLINT    NOT NULL CHECK (from_stage BETWEEN 2 AND 4),
    to_stage            SMALLINT    NOT NULL CHECK (to_stage BETWEEN 1 AND 3),
    reason              TEXT        NOT NULL,
    target_field_keys   JSONB       NOT NULL,
    returned_by         VARCHAR(50) NOT NULL,
    returned_at         TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_stage_returns_allowed CHECK (
        (from_stage = 2 AND to_stage = 1) OR (from_stage = 3 AND to_stage IN (1, 2)) OR (from_stage = 4 AND to_stage = 3)
    )
);
CREATE INDEX idx_stage_returns_request ON stage_returns (security_request_id);
COMMENT ON TABLE stage_returns IS '차수 반환 이력. 허용 반환 2→1, 3→2, 3→1, 4→3';
