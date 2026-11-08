-- F-02 문서 업로드·AI 요약. 파일은 서버 생성 파일명(UUID)으로 저장 폴더에 두고 원래 이름은 DB 에만.
-- 분석 작업 상태: QUEUED(분석 대기) → RUNNING(분석 중) → NEEDS_REVIEW(확인 필요 — 요약은 AI 제안이라 미확정) / FAILED.
-- 외래 키 제약은 두지 않는다(팀 결정).
CREATE TABLE uploaded_files
(
    uploaded_file_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_case_id BIGINT       NOT NULL,
    original_name    VARCHAR(255) NOT NULL,
    stored_name      VARCHAR(64)  NOT NULL UNIQUE,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL CHECK (size_bytes > 0),
    uploaded_by      VARCHAR(50)  NOT NULL,
    uploaded_at      TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_uploaded_files_case ON uploaded_files (business_case_id);
COMMENT ON TABLE uploaded_files IS '사업 건 업로드 문서. stored_name 은 서버가 만든 UUID — 경로 조작 방지';

CREATE TABLE analysis_jobs
(
    analysis_job_id  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_case_id BIGINT       NOT NULL,
    uploaded_file_id BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'NEEDS_REVIEW', 'FAILED')),
    summary          TEXT,
    model_reference  VARCHAR(200),
    error_message    VARCHAR(500),
    requested_by     VARCHAR(50)  NOT NULL,
    started_at       TIMESTAMPTZ,
    finished_at      TIMESTAMPTZ,
    revision         BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_analysis_jobs_case ON analysis_jobs (business_case_id, analysis_job_id);
COMMENT ON TABLE analysis_jobs IS 'AI 요약 작업. 요약은 AI 제안(미확정)이며 원문·프롬프트는 감사 이력에 남기지 않는다';
