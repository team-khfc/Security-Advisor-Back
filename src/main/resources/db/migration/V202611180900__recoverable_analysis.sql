ALTER TABLE analysis_jobs ADD COLUMN attempts integer NOT NULL DEFAULT 0;
CREATE INDEX analysis_jobs_pending_idx ON analysis_jobs(status, started_at, analysis_job_id)
WHERE status IN ('QUEUED', 'RUNNING');
