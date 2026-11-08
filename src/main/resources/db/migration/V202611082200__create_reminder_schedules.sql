-- 기한 알림 스케줄(F-13·F-17): 기관 관리자가 API 로 바꾸고 재시작 없이 반영. 시간대는 Asia/Seoul 고정.
CREATE TABLE reminder_schedules
(
    reminder_schedule_id BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                 VARCHAR(30)  NOT NULL UNIQUE,
    cron_expression      VARCHAR(100) NOT NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by           VARCHAR(50),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE reminder_schedules IS '예약 작업 스케줄. cron_expression 은 Spring 6필드(초 분 시 일 월 요일), Asia/Seoul';

INSERT INTO reminder_schedules (name, cron_expression, enabled) VALUES ('DEADLINE', '0 10 8 * * *', TRUE);
