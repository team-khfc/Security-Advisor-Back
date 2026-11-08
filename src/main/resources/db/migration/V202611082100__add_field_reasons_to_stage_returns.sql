-- 차수 반환의 항목별 사유(F-05): 지정 항목마다 "무엇을 어떻게 보완해야 하는지"를 따로 적는다. 키는 target_field_keys 의 항목.
ALTER TABLE stage_returns ADD COLUMN field_reasons JSONB NOT NULL DEFAULT '{}'::jsonb;
COMMENT ON COLUMN stage_returns.field_reasons IS '항목 키 → 항목별 반환 사유. 신청 단위 사유(reason)와 별개';
