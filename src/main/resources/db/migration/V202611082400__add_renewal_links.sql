-- 방화벽 규칙 연장(F-15 P3): 기존 규칙을 고치지 않고, 규칙을 참조해 미리 채운 새 신청을 만든다. 양쪽에 참조만 둔다(FK 없음).
ALTER TABLE security_requests ADD COLUMN renews_firewall_rule_id BIGINT;
COMMENT ON COLUMN security_requests.renews_firewall_rule_id IS '이 신청이 연장하려는 방화벽 규칙(연장 신청일 때만)';
ALTER TABLE firewall_rules ADD COLUMN renewal_request_id BIGINT;
COMMENT ON COLUMN firewall_rules.renewal_request_id IS '이 규칙을 연장하려고 만든 신청(있으면). 규칙 값은 바뀌지 않는다';
