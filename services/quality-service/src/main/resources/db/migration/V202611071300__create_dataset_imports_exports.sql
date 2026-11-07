-- SPEC-030: 외부 데이터셋 가져오기, 데이터셋 Golden Set 항목, source.completed 결과 경로, 특징 내보내기

-- 데이터셋 Golden Set의 출처·사용 조건. 가져오기마다 매니페스트 값으로 바꾼다(R-10 datasetOrigin).
ALTER TABLE golden_sets ADD COLUMN dataset_origin JSONB;
ALTER TABLE golden_sets ADD CONSTRAINT ck_golden_sets_origin CHECK (origin IN ('review', 'import'));

CREATE TABLE dataset_imports (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- uuid5(가져오기 네임스페이스, manifestKey). 같은 매니페스트를 다시 가져오면 같은 importId라
    -- media의 (importId, externalId) videoId가 같아진다(US-1 "같은 videoId가 다시 쓰임", D-30).
    import_id        UUID             NOT NULL UNIQUE,
    manifest_key     VARCHAR(200)     NOT NULL UNIQUE,
    dataset_name     VARCHAR(51),
    -- 매니페스트 origin(출처·사용 조건). 이어받은 작업자가 매니페스트를 다시 읽지 않고 Golden Set에 쓴다(R-10).
    dataset_origin   JSONB,
    eval_ratio       DOUBLE PRECISION NOT NULL,
    split_seed       BIGINT           NOT NULL,
    status           VARCHAR(20)      NOT NULL,
    video_count      INT              NOT NULL DEFAULT 0,
    registered_count INT              NOT NULL DEFAULT 0,
    failed_count     INT              NOT NULL DEFAULT 0,
    label_count      INT              NOT NULL DEFAULT 0,
    dev_count        INT              NOT NULL DEFAULT 0,
    eval_count       INT              NOT NULL DEFAULT 0,
    dev_set_id       UUID,
    eval_set_id      UUID,
    dev_version      INT,
    eval_version     INT,
    -- 처음 100건(R-7). 개수는 failed_count가 모두 센다.
    errors           JSONB            NOT NULL DEFAULT '[]',
    requested_by     UUID             NOT NULL,
    -- 작업 스레드가 살아 있다는 표시. 설정 시간보다 오래되면 다른 Pod가 이어받는다.
    heartbeat_at     TIMESTAMPTZ,
    requested_at     TIMESTAMPTZ      NOT NULL,
    finished_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT ck_dataset_imports_status
        CHECK (status IN ('PENDING', 'VALIDATING', 'REGISTERING', 'COMPLETED', 'FAILED'))
);
CREATE INDEX idx_dataset_imports_requested ON dataset_imports (requested_at DESC, id DESC);
CREATE INDEX idx_dataset_imports_work ON dataset_imports (status, heartbeat_at)
    WHERE status IN ('PENDING', 'VALIDATING', 'REGISTERING');

-- 검증을 통과한 영상과 라벨. 이어받은 Pod가 매니페스트를 다시 읽지 않고 남은 영상부터 등록한다.
CREATE TABLE dataset_import_videos (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    import_id    BIGINT       NOT NULL REFERENCES dataset_imports (id) ON DELETE CASCADE,
    external_id  VARCHAR(128) NOT NULL,
    path         VARCHAR(420) NOT NULL,
    content_type VARCHAR(40)  NOT NULL,
    split        VARCHAR(10)  NOT NULL,
    labels       JSONB        NOT NULL,
    label_count  INT          NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    source_id    UUID,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (import_id, external_id),
    CONSTRAINT ck_dataset_import_videos_split CHECK (split IN ('dev', 'eval')),
    CONSTRAINT ck_dataset_import_videos_status CHECK (status IN ('PENDING', 'REGISTERED', 'FAILED'))
);
CREATE INDEX idx_dataset_import_videos_pending ON dataset_import_videos (import_id, id)
    WHERE status = 'PENDING';

-- 데이터셋 Golden Set의 초안 항목(검토 항목 초안과 따로). item_id가 전체에서 유일해
-- 분할이 바뀐 다시 가져오기에서 항목이 dev·eval 두 세트에 함께 남지 않는다.
CREATE TABLE golden_dataset_items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- uuid5(데이터셋 항목 네임스페이스, datasetName + ":" + externalId)
    item_id       UUID         NOT NULL UNIQUE,
    golden_set_id BIGINT       NOT NULL REFERENCES golden_sets (id),
    external_id   VARCHAR(128) NOT NULL,
    source_id     UUID         NOT NULL,
    import_id     UUID         NOT NULL,
    labels        JSONB        NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_golden_dataset_items_set ON golden_dataset_items (golden_set_id, id);

-- source.completed마다 영상별 최신 결과 파일(R-13). 같은 키(sourceId)는 같은 파티션이라 받은 순서가 최신이다.
CREATE TABLE source_results (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id   UUID         NOT NULL UNIQUE,
    job_id      UUID         NOT NULL,
    -- 같은 job의 늦은 재발행(source.completed revision)이 더 낮으면 버린다(계약 source.completed revision).
    revision    INT          NOT NULL,
    result_key  VARCHAR(200) NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL,
    -- received_at의 UTC 날짜(R-14 기간)
    received_on DATE         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_source_results_received_on ON source_results (received_on);

CREATE TABLE feature_exports (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    export_id     UUID        NOT NULL UNIQUE,
    status        VARCHAR(20) NOT NULL,
    golden_set_id UUID,
    from_date     DATE,
    to_date       DATE,
    file_key      VARCHAR(200),
    row_count     INT         NOT NULL DEFAULT 0,
    source_count  INT         NOT NULL DEFAULT 0,
    requested_by  UUID        NOT NULL,
    heartbeat_at  TIMESTAMPTZ,
    requested_at  TIMESTAMPTZ NOT NULL,
    finished_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_feature_exports_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    -- R-15: Golden Set과 기간 중 하나만
    CONSTRAINT ck_feature_exports_scope
        CHECK ((golden_set_id IS NULL) <> (from_date IS NULL AND to_date IS NULL))
);
CREATE INDEX idx_feature_exports_work ON feature_exports (status, heartbeat_at)
    WHERE status IN ('PENDING', 'RUNNING');
