ALTER TABLE notification_outbox ADD COLUMN claim_token uuid;
CREATE TABLE notification_retry_records (
 notification_retry_record_id bigserial PRIMARY KEY,
 notification_outbox_id bigint NOT NULL,
 actor varchar(50) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);
