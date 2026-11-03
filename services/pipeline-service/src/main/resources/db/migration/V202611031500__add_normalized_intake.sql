CREATE TABLE normalization_results (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 normalization_id UUID NOT NULL UNIQUE, source_id UUID NOT NULL, owner_id UUID NOT NULL,
 normalization_version VARCHAR(100) NOT NULL, result VARCHAR(16) NOT NULL CHECK(result IN ('READY','REJECTED')),
 duration_ms BIGINT CHECK(duration_ms > 0), metadata JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK ((result='READY' AND duration_ms IS NOT NULL) OR (result='REJECTED' AND duration_ms IS NULL))
);
CREATE TABLE analysis_settings (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, version VARCHAR(100) NOT NULL UNIQUE, definition JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE normalized_job_plans (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 normalization_id UUID NOT NULL REFERENCES normalization_results(normalization_id),
 settings_version VARCHAR(100) NOT NULL REFERENCES analysis_settings(version),
 job_plan_id UUID NOT NULL UNIQUE REFERENCES jobs(plan_id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(normalization_id,settings_version)
);
CREATE TABLE normalized_receipts (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, event_id UUID NOT NULL UNIQUE,
 normalization_id UUID NOT NULL REFERENCES normalization_results(normalization_id), job_plan_id UUID REFERENCES jobs(plan_id),
 result VARCHAR(16) NOT NULL CHECK(result IN ('READY','REJECTED')), payload JSONB NOT NULL, trace_parent TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK ((result='READY' AND job_plan_id IS NOT NULL) OR (result='REJECTED' AND job_plan_id IS NULL))
);
