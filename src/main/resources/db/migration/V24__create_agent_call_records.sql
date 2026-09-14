CREATE TABLE agent_call_records (
    id BIGSERIAL PRIMARY KEY,
    agent_run_id BIGINT NOT NULL REFERENCES agent_runs(id),
    attempt_no INTEGER NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    run_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_json JSONB NOT NULL,
    response_json JSONB,
    error_code VARCHAR(100),
    error_message TEXT,
    retryable BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    duration_ms BIGINT,
    CONSTRAINT agent_call_records_attempt_check CHECK (attempt_no > 0),
    CONSTRAINT agent_call_records_status_check CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT agent_call_records_duration_check CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT agent_call_records_run_attempt_unique UNIQUE (agent_run_id, attempt_no)
);

CREATE INDEX idx_agent_call_records_run_created
    ON agent_call_records(agent_run_id, created_at DESC);
CREATE INDEX idx_agent_call_records_provider_status
    ON agent_call_records(provider, status, created_at DESC);
