CREATE TABLE task_packages (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    package_version INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    content_markdown TEXT NOT NULL,
    content_json JSONB NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    source_task_version BIGINT NOT NULL,
    source_plan_version INTEGER NOT NULL,
    source_spec_version INTEGER,
    source_profile_version INTEGER,
    base_commit VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    superseded_by BIGINT,
    CONSTRAINT task_packages_unique UNIQUE (task_id, package_version),
    CONSTRAINT task_packages_status_check CHECK (status IN ('CURRENT', 'STALE', 'RETIRED')),
    CONSTRAINT task_packages_version_check CHECK (package_version >= 1),
    CONSTRAINT task_packages_superseded_fk FOREIGN KEY (superseded_by) REFERENCES task_packages(id)
);

CREATE UNIQUE INDEX task_packages_one_current ON task_packages(task_id) WHERE status = 'CURRENT';
CREATE INDEX idx_task_packages_current ON task_packages(task_id, status);
