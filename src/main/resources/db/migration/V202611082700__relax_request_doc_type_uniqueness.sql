-- 사업 건·서류 유형당 신청 1건 제약을 "진행 중(종료 아님)" 신청으로 한정한다.
-- 확정·종결·철회된 신청이 있어도 같은 유형을 다시 신청할 수 있어야 한다(F-01 서류 추가, F-15 규칙 연장 — 확정된 방화벽 신청 뒤 새 방화벽 신청).
ALTER TABLE security_requests DROP CONSTRAINT uq_security_requests_case_doc_type;
CREATE UNIQUE INDEX uq_security_requests_case_doc_type_open
    ON security_requests (business_case_id, doc_type)
    WHERE status NOT IN ('CONFIRMED', 'CLOSED', 'WITHDRAWN');
COMMENT ON INDEX uq_security_requests_case_doc_type_open IS '사업 건·서류 유형당 진행 중 신청 1건(종료 상태 제외)';
