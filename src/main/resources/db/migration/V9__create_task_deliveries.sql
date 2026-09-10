CREATE TABLE task_deliveries (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    submitted_by BIGINT NOT NULL REFERENCES users(id),
    package_id BIGINT NOT NULL REFERENCES task_packages(id),
    package_version INTEGER NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    report_json JSONB NOT NULL,
    branch_name VARCHAR(200) NOT NULL,
    commit_sha VARCHAR(100) NOT NULL,
    pull_request_url VARCHAR(500),
    status VARCHAR(30) NOT NULL,
    rejection_reason TEXT,
    submitted_at TIMESTAMPTZ NOT NULL,
    reviewed_at TIMESTAMPTZ,
    CONSTRAINT task_deliveries_task_commit_unique UNIQUE (task_id, commit_sha),
    CONSTRAINT task_deliveries_package_version_check CHECK (package_version >= 1),
    CONSTRAINT task_deliveries_outcome_check CHECK (outcome IN ('READY_FOR_REVIEW', 'BLOCKED', 'FAILED')),
    CONSTRAINT task_deliveries_status_check CHECK (status IN ('SUBMITTED', 'CI_RUNNING', 'PASSED', 'FAILED', 'REJECTED'))
);

CREATE INDEX idx_task_deliveries_task_submitted
    ON task_deliveries(task_id, submitted_at DESC);
