-- F-17 화면 안 알림. 업무 상태를 바꾸지 않고, 생성 실패가 업무 처리를 실패시키지 않는다(불변식 30). 같은 사건은 한 번만(UNIQUE).
CREATE TABLE notifications
(
    notification_id    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_username VARCHAR(50)  NOT NULL,
    event_key          VARCHAR(200) NOT NULL,
    kind               VARCHAR(40)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    link               VARCHAR(300),
    read_at            TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_recipient_event UNIQUE (recipient_username, event_key)
);
CREATE INDEX idx_notifications_recipient ON notifications (recipient_username, read_at, notification_id);
COMMENT ON TABLE notifications IS '수신자별 업무 이벤트 안내. 제목에는 값·비밀을 넣지 않고 참조 ID 만(불변식 25)';
