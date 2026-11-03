CREATE TABLE jobs (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 plan_id UUID NOT NULL UNIQUE,
 source_id UUID NOT NULL,
 duration NUMERIC NOT NULL CHECK (duration > 0),
 profile VARCHAR(100) NOT NULL,
 chunk_length NUMERIC NOT NULL CHECK (chunk_length > 0),
 overlap NUMERIC NOT NULL CHECK (overlap >= 0),
 status VARCHAR(32) NOT NULL CHECK (status IN ('PLANNED','RUNNING','ANALYZED','COMPLETED','COMPLETED_WITH_ERRORS','TIMED_OUT','CANCELLED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- SPEC-004 R-17: 프로세스 간 동시 생성도 DB가 최종 방어한다.
CREATE UNIQUE INDEX one_active_job_per_source ON jobs(source_id) WHERE status IN ('PLANNED','RUNNING','ANALYZED');
CREATE TABLE chunks (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 job_id BIGINT NOT NULL REFERENCES jobs(id),
 ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
 owned_start NUMERIC NOT NULL CHECK (owned_start >= 0),
 owned_end NUMERIC NOT NULL CHECK (owned_end > owned_start),
 expanded_start NUMERIC NOT NULL CHECK (expanded_start >= 0 AND expanded_start <= owned_start),
 expanded_end NUMERIC NOT NULL CHECK (expanded_end >= owned_end),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(job_id,ordinal)
);
