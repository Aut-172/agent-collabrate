CREATE TABLE task_blockers (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    delivery_id BIGINT REFERENCES task_deliveries(id),
    status VARCHAR(20) NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    details TEXT,
    evidence_json JSONB,
    question TEXT,
    reported_by BIGINT NOT NULL REFERENCES users(id),
    resolved_by BIGINT REFERENCES users(id),
    resolution TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT task_blockers_status_check CHECK (status IN ('OPEN', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT task_blockers_reason_check CHECK (reason_code IN (
        'REQUIREMENT_CLARIFICATION', 'SPEC_CONFLICT', 'DEPENDENCY', 'ENVIRONMENT',
        'PERMISSION', 'CI_FAILURE', 'TASK_PACKAGE_UPDATED', 'OTHER'
    )),
    CONSTRAINT task_blockers_resolution_check CHECK (
        (status = 'OPEN' AND resolved_by IS NULL AND resolution IS NULL AND resolved_at IS NULL)
        OR (status IN ('RESOLVED', 'CANCELLED') AND resolved_by IS NOT NULL
            AND resolution IS NOT NULL AND resolved_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX task_blockers_one_open_per_task
    ON task_blockers(task_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_task_blockers_task_created
    ON task_blockers(task_id, created_at DESC);
