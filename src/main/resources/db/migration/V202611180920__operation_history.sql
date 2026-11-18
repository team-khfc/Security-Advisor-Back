CREATE TABLE operation_samples (
 operation_sample_id bigserial PRIMARY KEY,
 recorded_at timestamptz NOT NULL DEFAULT now(),
 instance_id varchar(36) NOT NULL,
 metrics text NOT NULL
);
CREATE INDEX operation_samples_time_idx ON operation_samples(recorded_at);
CREATE TABLE operation_alerts (
 operation_alert_id bigserial PRIMARY KEY,
 code varchar(50) NOT NULL,
 message varchar(300) NOT NULL,
 opened_at timestamptz NOT NULL DEFAULT now(),
 resolved_at timestamptz,
 acknowledged_by varchar(50),
 acknowledged_at timestamptz
);
CREATE UNIQUE INDEX operation_alerts_open_idx ON operation_alerts(code) WHERE resolved_at IS NULL;
