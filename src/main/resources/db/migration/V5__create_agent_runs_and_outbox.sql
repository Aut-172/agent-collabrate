CREATE TABLE agent_runs (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows(id),
    task_id BIGINT,
    run_type VARCHAR(40) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_summary TEXT NOT NULL,
    response_summary TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT agent_runs_type_check CHECK (run_type IN (
        'GENERATE_DESIGN', 'GENERATE_SPEC', 'GENERATE_BUILD_PLAN'
    )),
    CONSTRAINT agent_runs_status_check CHECK (status IN (
        'QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'
    )),
    CONSTRAINT agent_runs_retry_count_check CHECK (retry_count >= 0)
);

CREATE UNIQUE INDEX agent_runs_active_type_unique
    ON agent_runs(workflow_id, run_type)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE TABLE outbox_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(40) NOT NULL,
    reference_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT outbox_jobs_reference_unique UNIQUE (job_type, reference_id),
    CONSTRAINT outbox_jobs_type_check CHECK (job_type IN ('AGENT_RUN', 'GIT_SYNC', 'CI_SYNC')),
    CONSTRAINT outbox_jobs_status_check CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT outbox_jobs_attempt_count_check CHECK (attempt_count >= 0)
);

CREATE INDEX idx_outbox_jobs_due ON outbox_jobs(status, next_attempt_at);

ALTER TABLE document_versions
    ADD CONSTRAINT document_versions_agent_run_fk
        FOREIGN KEY (agent_run_id) REFERENCES agent_runs(id);
