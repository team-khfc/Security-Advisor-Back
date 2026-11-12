-- 화면의 상시 종료일 표현을 정책 도메인의 종료일 없음으로 정규화한다.
-- 원본 제출본과 장비 적용 기록은 바꾸지 않고 기존 재검토일이 있으면 보존한다.
UPDATE firewall_rules
SET end_date = NULL,
    review_date = COALESCE(review_date, (start_date + INTERVAL '12 months')::date),
    revision = revision + 1,
    updated_at = now()
WHERE end_date = DATE '9999-12-31';
