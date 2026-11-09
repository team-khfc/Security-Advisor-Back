-- 최종 초안의 출처·원본 기준·사용자 확인을 제출 전까지 관리한다. 제출 시 JSON 스냅샷으로 복사한다.
CREATE TABLE final_drafts (
    final_draft_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_request_id BIGINT NOT NULL,
    source_fingerprint VARCHAR(64) NOT NULL,
    original_text TEXT NOT NULL,
    text TEXT NOT NULL,
    value_source VARCHAR(30) NOT NULL CHECK (value_source IN ('AI_INFERRED', 'USER_ANSWER')),
    model_reference VARCHAR(200),
    confirmed_by VARCHAR(50),
    confirmed_at TIMESTAMPTZ,
    invalidated BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((confirmed_by IS NULL) = (confirmed_at IS NULL))
);
CREATE INDEX idx_final_drafts_request ON final_drafts (security_request_id, final_draft_id DESC);
