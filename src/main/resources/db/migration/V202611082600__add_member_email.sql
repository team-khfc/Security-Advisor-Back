-- 외부 알림 채널(F-17 P3)용 수신 주소. 선택 항목이며 시연 계정은 비워 둔다(합성 데이터 원칙 — 실존 주소 금지).
ALTER TABLE members ADD COLUMN email VARCHAR(200);
COMMENT ON COLUMN members.email IS '메일 알림 수신 주소(선택). 본인 또는 기관 관리자가 설정';
