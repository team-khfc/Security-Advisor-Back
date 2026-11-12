ALTER TABLE firewall_rules
    ADD COLUMN review_months integer NOT NULL DEFAULT 12 CHECK (review_months BETWEEN 1 AND 60),
    ADD COLUMN review_notice_days integer NOT NULL DEFAULT 30 CHECK (review_notice_days BETWEEN 1 AND 90),
    ADD COLUMN review_decision varchar(30),
    ADD COLUMN review_history jsonb NOT NULL DEFAULT '[]'::jsonb;
CREATE INDEX idx_firewall_review_due ON firewall_rules(review_date) WHERE status = 'APPLIED' AND end_date IS NULL;
