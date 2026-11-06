-- SPEC-029 R-28: alert.raised의 후보별 pending 발행 시각과 confirmed·dismissed 시각
CREATE TABLE alert_events (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    candidate_id        UUID         NOT NULL UNIQUE,
    source_id           UUID         NOT NULL,
    camera_id           UUID         NOT NULL,
    event_type          VARCHAR(50)  NOT NULL,
    severity            VARCHAR(10)  NOT NULL,
    -- pending alert.raised의 occurredAt. 순서가 뒤집혀 confirmed·dismissed가 먼저 오면 나중에 채운다.
    pending_at          TIMESTAMPTZ,
    resolved_at         TIMESTAMPTZ,
    status              VARCHAR(10)  NOT NULL,
    dismiss_reason      VARCHAR(20),
    evidence_frame_keys JSONB        NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_alert_events_status CHECK (status IN ('pending', 'confirmed', 'dismissed')),
    CONSTRAINT ck_alert_events_resolved CHECK ((status = 'pending') = (resolved_at IS NULL))
);
-- 리포트는 발행 날짜(없으면 판정 날짜)로 하루씩 묶는다.
CREATE INDEX idx_alert_events_day ON alert_events ((COALESCE(pending_at, resolved_at)));
CREATE INDEX idx_alert_events_camera_day ON alert_events (camera_id, (COALESCE(pending_at, resolved_at)));
CREATE INDEX idx_alert_events_source ON alert_events (source_id);
