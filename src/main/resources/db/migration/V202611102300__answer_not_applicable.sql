-- 기존 미해당 문자열을 정식 상태로 이관한다. 제출본 JSON은 불변으로 유지한다.
ALTER TABLE answers ADD COLUMN not_applicable BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE answers SET not_applicable = TRUE
WHERE BTRIM(value) = '미해당' AND value_source IS NOT NULL;
