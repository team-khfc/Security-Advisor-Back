-- 목록 페이지 정렬과 권한 조건의 조회 인덱스. 기존 기록은 변경하지 않는다.
CREATE INDEX idx_security_requests_created_id ON security_requests (created_at DESC, security_request_id DESC);
CREATE INDEX idx_security_requests_updated_id ON security_requests (updated_at DESC, security_request_id DESC);
CREATE INDEX idx_audit_events_occurred_id ON audit_events (occurred_at DESC, audit_event_id DESC);
CREATE INDEX idx_audit_events_target_order ON audit_events (target_type, target_id, occurred_at DESC, audit_event_id DESC);
