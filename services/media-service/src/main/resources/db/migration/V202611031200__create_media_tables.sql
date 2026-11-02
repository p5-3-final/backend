-- SPEC-003: 영상과 이벤트 Outbox

CREATE TABLE videos (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 외부(API id, 이벤트 sourceId)에는 이 값만 노출해 내부 순번이 드러나지 않게 한다.
    public_id           UUID          NOT NULL UNIQUE,
    -- auth-service 사용자 UUID. 다른 서비스 테이블이라 FK를 두지 않는다.
    owner_id            UUID          NOT NULL,
    -- 표시용 원래 파일 이름. 저장 경로에는 쓰지 않는다(SPEC-003 R-6).
    file_name           VARCHAR(255)  NOT NULL,
    size_bytes          BIGINT        NOT NULL,
    content_type        VARCHAR(40)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    storage_key         VARCHAR(200)  NOT NULL,
    -- 멀티파트 업로드일 때만 값이 있다. S3 upload ID는 길이 제한이 문서화돼 있지 않아 넉넉히 둔다.
    multipart_upload_id VARCHAR(1024),
    part_size_bytes     BIGINT,
    expires_at          TIMESTAMPTZ   NOT NULL,
    uploaded_at         TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    -- 만료·삭제 뒤 저장소 파일을 지운 시각. NULL이면 정리 작업이 다시 시도한다(SPEC-003 R-13, R-20).
    storage_cleaned_at  TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_videos_owner_created ON videos (owner_id, created_at DESC, id DESC)
    WHERE status <> 'DELETED';
CREATE INDEX idx_videos_uploading_expires ON videos (expires_at) WHERE status = 'UPLOADING';
CREATE INDEX idx_videos_cleanup ON videos (id)
    WHERE storage_cleaned_at IS NULL AND status IN ('EXPIRED', 'DELETED');

-- libs/common-messaging의 OutboxRecorder·OutboxPublisher가 쓰는 구조
CREATE TABLE outbox_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 이벤트 ID. 발행을 다시 시도해도 같다(소비자가 중복을 흡수하는 기준).
    event_id     UUID         NOT NULL UNIQUE,
    event_type   VARCHAR(100) NOT NULL,
    aggregate_id UUID         NOT NULL,
    payload      JSONB        NOT NULL,
    -- W3C traceparent는 55자다(00-32-16-2).
    trace_parent VARCHAR(55),
    attempts     INT          NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_events_pending ON outbox_events (id) WHERE published_at IS NULL;
