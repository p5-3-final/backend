-- SPEC-029: 검토 큐, 삭제 흔적, Golden Set, 평가 실행
CREATE TABLE review_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- uuid5(검토 항목 네임스페이스, eventId). 같은 이벤트를 다시 받아도 같은 값이다(R-1).
    item_id             UUID         NOT NULL UNIQUE,
    event_id            UUID         NOT NULL UNIQUE,
    source_id           UUID         NOT NULL,
    job_id              UUID         NOT NULL,
    -- event.analyzed의 candidateEventType(alert.raised면 candidateEventType). 표본 비율·목록 필터의 기준이다.
    event_type          VARCHAR(50)  NOT NULL,
    start_ms            BIGINT       NOT NULL,
    end_ms              BIGINT       NOT NULL,
    judgment_method     VARCHAR(50)  NOT NULL,
    taxonomy_version    VARCHAR(10)  NOT NULL,
    -- 계약 ReviewItem.reason(x-extensible-enum). 값 목록은 코드의 ReviewReason이 계약과 맞춘다(R-27).
    reason              VARCHAR(30)  NOT NULL,
    review_reasons      JSONB        NOT NULL,
    evidence_frame_keys JSONB        NOT NULL,
    -- _vlm-analysis 그대로. 판정이 없는 alert_dismissed 항목은 빈 객체다(R-28).
    analysis            JSONB        NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    assignee_id         UUID,
    assigned_at         TIMESTAMPTZ,
    label               JSONB,
    labeled_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_review_items_status CHECK (status IN ('UNASSIGNED', 'ASSIGNED', 'LABELED')),
    CONSTRAINT ck_review_items_assignee CHECK ((status = 'UNASSIGNED') = (assignee_id IS NULL))
);
CREATE INDEX idx_review_items_queue ON review_items (created_at, item_id);
CREATE INDEX idx_review_items_status_queue ON review_items (status, created_at, item_id);
-- 사유별 재판정 큐(R-27)
CREATE INDEX idx_review_items_reason_queue ON review_items (reason, created_at, item_id);
CREATE INDEX idx_review_items_stale ON review_items (assigned_at) WHERE status = 'ASSIGNED';
CREATE INDEX idx_review_items_source ON review_items (source_id);

-- video.deleted가 event.analyzed보다 먼저 올 수 있다(계약 video.deleted 설명). 늦게 온 이벤트로 항목을 다시 만들지 않는다.
CREATE TABLE deleted_sources (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_id  UUID        NOT NULL UNIQUE,
    deleted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE golden_sets (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    set_id            UUID         NOT NULL UNIQUE,
    name              VARCHAR(63)  NOT NULL UNIQUE,
    split             VARCHAR(10)  NOT NULL,
    -- 계약 GoldenSet.origin이 필수다. 검토 라벨 세트는 항상 review다(SPEC-030이 import를 쓴다).
    origin            VARCHAR(10)  NOT NULL DEFAULT 'review',
    description       VARCHAR(500),
    latest_version    INT,
    baseline_run_id   UUID,
    -- 확정 진행 표시. NULL이 아니면 확정 중이다(R-15). Pod가 죽어 남은 표시는 설정한 시간이 지나면 무시한다.
    freeze_started_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_golden_sets_split CHECK (split IN ('dev', 'eval'))
);

CREATE TABLE golden_draft_entries (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entry_id       UUID        NOT NULL UNIQUE,
    golden_set_id  BIGINT      NOT NULL REFERENCES golden_sets (id),
    -- 검토 항목이 지워지면(R-16) 초안 항목도 함께 지운다.
    review_item_id BIGINT      NOT NULL REFERENCES review_items (id) ON DELETE CASCADE,
    -- 넣을 때의 라벨 복사본(R-12)
    label          JSONB       NOT NULL,
    added_at       TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (golden_set_id, review_item_id)
);
CREATE INDEX idx_golden_draft_entries_set_added ON golden_draft_entries (golden_set_id, added_at, id);
CREATE INDEX idx_golden_draft_entries_review_item ON golden_draft_entries (review_item_id);

CREATE TABLE golden_set_versions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    golden_set_id BIGINT       NOT NULL REFERENCES golden_sets (id),
    version       INT          NOT NULL,
    entry_count   INT          NOT NULL,
    manifest_key  VARCHAR(200) NOT NULL,
    frozen_at     TIMESTAMPTZ  NOT NULL,
    frozen_by     UUID         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (golden_set_id, version)
);

CREATE TABLE evaluation_runs (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id                UUID             NOT NULL UNIQUE,
    golden_set_id         UUID             NOT NULL REFERENCES golden_sets (set_id),
    golden_set_version    INT              NOT NULL,
    kind                  VARCHAR(30)      NOT NULL,
    versions              JSONB            NOT NULL,
    metrics               JSONB            NOT NULL,
    started_at            TIMESTAMPTZ      NOT NULL,
    finished_at           TIMESTAMPTZ      NOT NULL,
    trigger               VARCHAR(20)      NOT NULL,
    commit_sha            VARCHAR(40),
    -- 받을 때 계산한 Gate 결과. 나중에 기준 실행을 바꿔도 다시 계산하지 않는다(R-18 "받을 때").
    gate_baseline_run_id  UUID,
    gate_passed           BOOLEAN,
    gate_tolerance        DOUBLE PRECISION NOT NULL,
    gate_failures         JSONB            NOT NULL,
    received_at           TIMESTAMPTZ      NOT NULL,
    created_at            TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ      NOT NULL DEFAULT now()
);
CREATE INDEX idx_evaluation_runs_received ON evaluation_runs (received_at DESC, id DESC);
CREATE INDEX idx_evaluation_runs_set_received ON evaluation_runs (golden_set_id, received_at DESC, id DESC);
