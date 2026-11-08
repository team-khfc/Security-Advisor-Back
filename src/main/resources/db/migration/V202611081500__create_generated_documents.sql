-- F-06 서류 초안/제출본/확정본 렌더링 결과. 값마다 출처·확인 상태를 담고 미확정은 "담당자 확인 필요"로 표기한다.
-- 1차 범위 형식은 Markdown(다운로드 .md). 외래 키 제약은 두지 않는다(팀 결정).
CREATE TABLE generated_documents
(
    generated_document_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    security_request_id   BIGINT       NOT NULL,
    submission_id         BIGINT,
    kind                  VARCHAR(20)  NOT NULL CHECK (kind IN ('DRAFT', 'SUBMITTED', 'CONFIRMED')),
    version               INTEGER      NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    sections              JSONB        NOT NULL,
    rendered_markdown     TEXT         NOT NULL,
    ai_supplement         TEXT,
    model_reference       VARCHAR(200),
    unconfirmed_count     INTEGER      NOT NULL DEFAULT 0,
    generated_by          VARCHAR(50)  NOT NULL,
    generated_at          TIMESTAMPTZ  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_generated_documents_request_version UNIQUE (security_request_id, version)
);
CREATE INDEX idx_generated_documents_request ON generated_documents (security_request_id, kind);
COMMENT ON TABLE generated_documents IS '서류 렌더링 결과. DRAFT 는 현재 답변, SUBMITTED/CONFIRMED 는 제출본 스냅샷에서 생성. ai_supplement 는 AI 보완 서술(미확정)';
