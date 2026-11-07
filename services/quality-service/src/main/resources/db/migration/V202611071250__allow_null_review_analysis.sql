-- 계약 quality-service 3.0.0(묶음 5.0.0): 판정 결과가 없는 alert_dismissed 검토 항목의 analysis는 null이다(SPEC-029 R-28).
-- 이미 빈 객체로 저장한 항목도 같은 뜻이라 null로 바꾼다.
ALTER TABLE review_items ALTER COLUMN analysis DROP NOT NULL;
UPDATE review_items SET analysis = NULL WHERE reason = 'alert_dismissed' AND analysis = '{}'::jsonb;
