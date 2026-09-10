ALTER TABLE task_packages DROP CONSTRAINT task_packages_superseded_fk;
ALTER TABLE task_packages ADD CONSTRAINT task_packages_superseded_fk
    FOREIGN KEY (superseded_by) REFERENCES task_packages(id) ON DELETE SET NULL;

CREATE TABLE task_package_confirmations (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    package_id BIGINT NOT NULL REFERENCES task_packages(id),
    package_version INTEGER NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    confirmation_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT task_package_confirmations_unique UNIQUE (task_id, user_id, package_version),
    CONSTRAINT task_package_confirmations_version_check CHECK (package_version >= 1),
    CONSTRAINT task_package_confirmations_type_check CHECK (
        confirmation_type IN ('START_DEVELOPMENT', 'RESUME_AFTER_BLOCKER')
    )
);

CREATE INDEX idx_task_package_confirmations_latest
    ON task_package_confirmations(task_id, user_id, created_at DESC);
