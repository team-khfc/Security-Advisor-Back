-- 현재 배정·계정 권한으로 알림을 조회하며 기존 이력은 삭제하지 않는다.
ALTER TABLE notifications ADD COLUMN security_request_id bigint REFERENCES security_requests(security_request_id);
ALTER TABLE notifications ADD COLUMN recipient_role varchar(20);
UPDATE notifications n SET recipient_role = m.role FROM members m WHERE m.username = n.recipient_username;
UPDATE notifications SET security_request_id = split_part(link, '/', 4)::bigint
WHERE link ~ '^/api/security-requests/[0-9]+(/|$)';
UPDATE notifications n SET security_request_id = s.security_request_id FROM submissions s
WHERE n.link = '/api/submissions/' || s.submission_id;
CREATE INDEX ix_notifications_recipient_page ON notifications(recipient_username, notification_id DESC);
