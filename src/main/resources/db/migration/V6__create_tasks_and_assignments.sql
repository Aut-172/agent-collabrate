CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows(id),
    external_key VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    effort_points INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    source_plan_version INTEGER NOT NULL,
    source_spec_version INTEGER,
    branch_name VARCHAR(200),
    current_package_version INTEGER,
    delivery_notes TEXT,
    result_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT tasks_workflow_key_unique UNIQUE (workflow_id, external_key),
    CONSTRAINT tasks_effort_points_check CHECK (effort_points BETWEEN 1 AND 8),
    CONSTRAINT tasks_source_plan_version_check CHECK (source_plan_version >= 1),
    CONSTRAINT tasks_source_spec_version_check CHECK (
        source_spec_version IS NULL OR source_spec_version >= 1
    ),
    CONSTRAINT tasks_package_version_check CHECK (
        current_package_version IS NULL OR current_package_version >= 1
    ),
    CONSTRAINT tasks_status_check CHECK (status IN (
        'TODO', 'ASSIGNED', 'IN_PROGRESS', 'BLOCKED', 'DELIVERY_SUBMITTED',
        'CI_RUNNING', 'DONE', 'FAILED', 'CANCELLED'
    ))
);

CREATE TABLE task_assignments (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    assignee_user_id BIGINT NOT NULL REFERENCES users(id),
    assigned_by BIGINT NOT NULL REFERENCES users(id),
    assignment_version INTEGER NOT NULL,
    assignment_reason TEXT NOT NULL,
    assignment_score NUMERIC(5,4),
    profile_version INTEGER NOT NULL,
    profile_snapshot JSONB NOT NULL,
    workload_snapshot JSONB NOT NULL,
    is_current BOOLEAN NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT task_assignments_version_unique UNIQUE (task_id, assignment_version),
    CONSTRAINT task_assignments_version_check CHECK (assignment_version >= 1),
    CONSTRAINT task_assignments_profile_version_check CHECK (profile_version >= 1),
    CONSTRAINT task_assignments_score_check CHECK (
        assignment_score IS NULL OR assignment_score BETWEEN 0 AND 1
    ),
    CONSTRAINT task_assignments_lifecycle_check CHECK (
        (is_current = TRUE AND ended_at IS NULL)
        OR (is_current = FALSE AND ended_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX task_assignments_one_current
    ON task_assignments(task_id)
    WHERE is_current = TRUE;

CREATE INDEX idx_tasks_workflow_status ON tasks(workflow_id, status);
CREATE INDEX idx_task_assignments_assignee_current
    ON task_assignments(assignee_user_id, is_current);

ALTER TABLE agent_runs
    ADD CONSTRAINT agent_runs_task_fk FOREIGN KEY (task_id) REFERENCES tasks(id);
