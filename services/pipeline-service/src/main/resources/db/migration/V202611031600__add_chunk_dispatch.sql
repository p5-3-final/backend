ALTER TABLE chunks ADD COLUMN chunk_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE;
ALTER TABLE chunks ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
 CHECK (status IN ('PENDING','DISPATCHED','FAILED'));
CREATE INDEX pending_chunks ON chunks(job_id,ordinal) WHERE status='PENDING';
CREATE TABLE dispatch_states (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY CHECK(id=1),
 sequence BIGINT NOT NULL DEFAULT 0,
 user_limit INTEGER NOT NULL CHECK(user_limit>0), source_limit INTEGER NOT NULL CHECK(source_limit>0),
 global_limit INTEGER NOT NULL CHECK(global_limit>0), start_window_ms BIGINT NOT NULL CHECK(start_window_ms>0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE dispatch_users (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, owner_id UUID NOT NULL UNIQUE,
 last_sequence BIGINT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE chunk_executions (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, execution_id UUID NOT NULL UNIQUE,
 chunk_id BIGINT NOT NULL REFERENCES chunks(id), event_id UUID NOT NULL UNIQUE,
 status VARCHAR(32) NOT NULL CHECK(status IN ('DISPATCHED','RECOVERY_REQUIRED','STOPPED')),
 start_before TIMESTAMPTZ NOT NULL,
 confirmation_id UUID UNIQUE, evidence VARCHAR(500),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK((status='STOPPED' AND confirmation_id IS NOT NULL AND evidence IS NOT NULL) OR
       (status<>'STOPPED' AND confirmation_id IS NULL AND evidence IS NULL))
);
CREATE UNIQUE INDEX one_live_execution_per_chunk ON chunk_executions(chunk_id) WHERE status IN ('DISPATCHED','RECOVERY_REQUIRED');
CREATE INDEX executions_expiring ON chunk_executions(start_before) WHERE status='DISPATCHED';
CREATE TABLE execution_slots (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 execution_id UUID NOT NULL UNIQUE REFERENCES chunk_executions(execution_id),
 owner_id UUID NOT NULL, source_id UUID NOT NULL, released_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX held_slots_owner ON execution_slots(owner_id) WHERE released_at IS NULL;
CREATE INDEX held_slots_source ON execution_slots(source_id) WHERE released_at IS NULL;
CREATE TABLE outbox_events (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, event_id UUID NOT NULL UNIQUE,
 event_type VARCHAR(100) NOT NULL, aggregate_id UUID NOT NULL, payload JSONB NOT NULL,
 trace_parent VARCHAR(55), attempts INT NOT NULL DEFAULT 0, published_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_events_pending ON outbox_events(id) WHERE published_at IS NULL;
