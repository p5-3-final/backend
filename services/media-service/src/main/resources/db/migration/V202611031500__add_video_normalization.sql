-- SPEC-007: 정규화 결과. 기존 영상은 정규화 전이라 모두 NULL이다.

-- 반영한 정규화 결과(불변). 같은 영상을 다시 정규화하면 새 ID가 온다(ADR-0009).
ALTER TABLE videos ADD COLUMN normalization_id      UUID;
ALTER TABLE videos ADD COLUMN normalization_version VARCHAR(100);
-- 정규화 사본·HLS 재생 목록 키. 다음 "정규화 사본 재생" spec에서 재생 주소를 만들 때 쓴다.
ALTER TABLE videos ADD COLUMN normalized_key        VARCHAR(300);
ALTER TABLE videos ADD COLUMN hls_playlist_key      VARCHAR(300);
-- 연동 시간은 정수 밀리초다(ADR-0009). 응답의 초 값은 여기서 계산한다.
ALTER TABLE videos ADD COLUMN duration_ms           BIGINT;
ALTER TABLE videos ADD COLUMN estimated_processing_sec INTEGER;
ALTER TABLE videos ADD COLUMN reject_reason         VARCHAR(30);
-- READY·REJECTED로 바뀐 시각
ALTER TABLE videos ADD COLUMN normalized_at         TIMESTAMPTZ;
