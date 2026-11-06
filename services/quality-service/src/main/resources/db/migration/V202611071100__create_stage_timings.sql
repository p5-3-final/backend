-- SPEC-029 R-21~R-25: 처리 단계 측정 원본(월 단위 범위 파티션)과 일 단위 집계
-- 파티션 키를 PK에 넣어야 해서 PK가 (id, started_at)이다.
CREATE TABLE stage_timings (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    timing_id   UUID             NOT NULL,
    stage_id    VARCHAR(100)     NOT NULL,
    path        VARCHAR(500)     NOT NULL,
    source_id   UUID,
    service     VARCHAR(100)     NOT NULL,
    attempt     INT              NOT NULL,
    started_at  TIMESTAMPTZ      NOT NULL,
    ended_at    TIMESTAMPTZ      NOT NULL,
    duration_ms DOUBLE PRECISION NOT NULL,
    status      VARCHAR(10)      NOT NULL,
    versions    JSONB            NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    PRIMARY KEY (id, started_at),
    CONSTRAINT ck_stage_timings_status CHECK (status IN ('ok', 'error'))
) PARTITION BY RANGE (started_at);
CREATE INDEX idx_stage_timings_timing ON stage_timings (timing_id);
CREATE INDEX idx_stage_timings_source ON stage_timings (source_id, started_at);
CREATE INDEX idx_stage_timings_stage ON stage_timings (stage_id, started_at);

-- 마이그레이션 시점의 이번 달부터 3개월을 만든다. 이후는 StageTimingPartitionJob이 미리 만든다(R-25).
-- 이름 규칙(stage_timings_yYYYYmMM)은 파티션 작업과 같다.
DO $$
DECLARE
    month_start DATE := date_trunc('month', now() AT TIME ZONE 'UTC')::date;
    i INT;
    lower_bound DATE;
BEGIN
    FOR i IN 0..3 LOOP
        lower_bound := (month_start + make_interval(months => i))::date;
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF stage_timings FOR VALUES FROM (%L) TO (%L)',
            'stage_timings_y' || to_char(lower_bound, 'YYYY') || 'm' || to_char(lower_bound, 'MM'),
            lower_bound::timestamp AT TIME ZONE 'UTC',
            (lower_bound + INTERVAL '1 month')::timestamp AT TIME ZONE 'UTC');
    END LOOP;
END
$$;

CREATE TABLE stage_daily_stats (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    stat_date     DATE             NOT NULL,
    stage_id      VARCHAR(100)     NOT NULL,
    -- 둘 다 NULL이면 단계 전체 집계, 둘 다 있으면 그 버전 키·값의 집계다(R-23).
    version_key   VARCHAR(30),
    version_value VARCHAR(200),
    record_count  BIGINT           NOT NULL,
    p50_ms        DOUBLE PRECISION NOT NULL,
    p95_ms        DOUBLE PRECISION NOT NULL,
    error_count   BIGINT           NOT NULL,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT uq_stage_daily_stats UNIQUE NULLS NOT DISTINCT (stat_date, stage_id, version_key, version_value),
    CONSTRAINT ck_stage_daily_stats_version CHECK ((version_key IS NULL) = (version_value IS NULL))
);
CREATE INDEX idx_stage_daily_stats_trend ON stage_daily_stats (stage_id, version_key, version_value, stat_date);
