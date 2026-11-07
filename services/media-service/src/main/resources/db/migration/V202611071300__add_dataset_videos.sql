-- SPEC-030 R-5: 외부 데이터셋 영상은 보존 등급 DATASET(ADR-0011, overview 보존 표 외부 데이터셋 행).
-- 이번에는 등급만 저장한다. 삭제·보존 정리 동작은 바꾸지 않는다(plan 10절 4, D-29).
ALTER TABLE videos ADD COLUMN retention_grade VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE videos ADD CONSTRAINT ck_videos_retention_grade CHECK (retention_grade IN ('STANDARD', 'DATASET'));
