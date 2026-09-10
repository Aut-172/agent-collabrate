CREATE TABLE git_operations (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    workflow_id BIGINT NOT NULL REFERENCES workflows(id),
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    delivery_id BIGINT NOT NULL REFERENCES task_deliveries(id),
    operation_type VARCHAR(40) NOT NULL,
    branch_name VARCHAR(200),
    commit_sha VARCHAR(100),
    pull_request_number INTEGER,
    pull_request_url VARCHAR(500),
    external_id VARCHAR(200),
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT git_operations_type_check CHECK (operation_type IN ('VALIDATE_DELIVERY')),
    CONSTRAINT git_operations_status_check CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT git_operations_delivery_unique UNIQUE (delivery_id)
);

CREATE TABLE ci_runs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    workflow_id BIGINT NOT NULL REFERENCES workflows(id),
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    delivery_id BIGINT NOT NULL REFERENCES task_deliveries(id),
    commit_sha VARCHAR(100) NOT NULL,
    external_id VARCHAR(200),
    status VARCHAR(30) NOT NULL,
    conclusion VARCHAR(50),
    details_url VARCHAR(500),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    last_synced_at TIMESTAMPTZ NOT NULL,
    configuration_present BOOLEAN,
    configuration_recognized BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ci_runs_status_check CHECK (status IN ('PENDING', 'RUNNING', 'PASSED', 'FAILED', 'UNKNOWN')),
    CONSTRAINT ci_runs_delivery_commit_unique UNIQUE (delivery_id, commit_sha)
);

CREATE INDEX idx_git_operations_task_created ON git_operations(task_id, created_at DESC);
CREATE INDEX idx_ci_runs_task_created ON ci_runs(task_id, created_at DESC);
