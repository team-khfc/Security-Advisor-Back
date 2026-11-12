CREATE TABLE preparation_materials (
 security_request_id BIGINT PRIMARY KEY REFERENCES security_requests(security_request_id),
 revision BIGINT NOT NULL DEFAULT 0,
 content TEXT NOT NULL
);
COMMENT ON TABLE preparation_materials IS '신청별 조건부 준비자료 연결 및 담당자 검토 상태';
