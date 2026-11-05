ALTER TABLE jobs ADD COLUMN execution_sequence BIGINT NOT NULL DEFAULT 0 CHECK(execution_sequence>=0);
ALTER TABLE jobs ADD COLUMN max_attempts INTEGER NOT NULL DEFAULT 1 CHECK(max_attempts BETWEEN 1 AND 10);
ALTER TABLE jobs ADD COLUMN retryable_error_codes TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE jobs ADD COLUMN retry_delay_ms BIGINT NOT NULL DEFAULT 1000 CHECK(retry_delay_ms BETWEEN 0 AND 86400000);
ALTER TABLE chunks ADD COLUMN retry_at TIMESTAMPTZ;
ALTER TABLE chunks DROP CONSTRAINT chunks_status_check;
ALTER TABLE chunks ADD CONSTRAINT chunks_status_check CHECK(status IN ('PENDING','RETRY_PENDING','DISPATCHED','PROCESSED','FAILED'));
CREATE TABLE job_execution_summaries (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 job_id BIGINT NOT NULL UNIQUE REFERENCES jobs(id),
 sequence BIGINT NOT NULL CHECK(sequence>0), snapshot JSONB NOT NULL,
 checked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX summaries_check ON job_execution_summaries(checked_at,job_id);
