ALTER TABLE chunks DROP CONSTRAINT chunks_status_check;
ALTER TABLE chunks ADD CONSTRAINT chunks_status_check CHECK(status IN ('PENDING','DISPATCHED','PROCESSED','FAILED'));
ALTER TABLE chunk_executions DROP CONSTRAINT chunk_executions_status_check;
ALTER TABLE chunk_executions ADD CONSTRAINT chunk_executions_status_check CHECK(status IN ('DISPATCHED','RECOVERY_REQUIRED','STOPPED','SUCCEEDED','FAILED'));
ALTER TABLE chunk_executions ADD COLUMN result_payload JSONB;
CREATE TABLE execution_result_receipts (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 event_id UUID NOT NULL UNIQUE, execution_id UUID NOT NULL REFERENCES chunk_executions(execution_id),
 payload JSONB NOT NULL, decision VARCHAR(16) NOT NULL CHECK(decision IN ('APPLIED','DUPLICATE','IGNORED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
