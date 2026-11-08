-- 보완 대기 중 담당자 협의 기한 정지(F-13 P3). 저장형 기한 규칙을 지키기 위해 정지 = 종료(paused 표시 + 남은 영업일 저장),
-- 재개 = 남은 영업일로 새 기한 행. 이전 행은 지우지 않는다.
ALTER TABLE deadlines ADD COLUMN paused BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE deadlines ADD COLUMN remaining_business_days INTEGER;
COMMENT ON COLUMN deadlines.paused IS '보완 대기로 정지되어 종료된 기한(재개 시 false 로 바뀌고 새 행이 생긴다)';
COMMENT ON COLUMN deadlines.remaining_business_days IS '정지 시점에 남아 있던 영업일(재개 기한 계산용)';
