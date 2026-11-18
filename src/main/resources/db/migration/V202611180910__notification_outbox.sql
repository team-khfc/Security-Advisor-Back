CREATE TABLE notification_outbox (
 notification_outbox_id bigserial PRIMARY KEY,
 recipient varchar(50) NOT NULL,
 event_key varchar(500) NOT NULL,
 channel varchar(20) NOT NULL,
 kind varchar(80) NOT NULL,
 title text NOT NULL,
 link text NOT NULL,
 notification_id bigint,
 status varchar(20) NOT NULL DEFAULT 'PENDING',
 attempts integer NOT NULL DEFAULT 0,
 available_at timestamptz NOT NULL DEFAULT now(),
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(recipient, event_key, channel)
);
CREATE INDEX notification_outbox_due_idx ON notification_outbox(status, available_at);
