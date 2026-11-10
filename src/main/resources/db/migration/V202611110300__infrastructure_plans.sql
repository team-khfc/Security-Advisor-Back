CREATE TABLE infrastructure_plans (
 security_request_id bigint PRIMARY KEY REFERENCES security_requests(security_request_id),
 revision bigint NOT NULL DEFAULT 0,
 content text NOT NULL,
 updated_by varchar(50) NOT NULL,
 updated_at timestamptz NOT NULL
);
COMMENT ON TABLE infrastructure_plans IS '배정된 ICT인프라 담당자의 서버·통신 구간 검토 정보';
