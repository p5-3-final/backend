-- 서비스 마이그레이션과 같은 outbox_events 구조 (media-service V202611031200 참고)
CREATE TABLE IF NOT EXISTS outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     UUID         NOT NULL UNIQUE,
    event_type   VARCHAR(100) NOT NULL,
    aggregate_id UUID         NOT NULL,
    payload      JSONB        NOT NULL,
    trace_parent VARCHAR(55),
    attempts     INT          NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
