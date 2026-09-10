ALTER TABLE projects
    ADD COLUMN latest_context_commit VARCHAR(100);

CREATE TABLE code_context_runs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    requested_by BIGINT REFERENCES users(id),
    run_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    inventory_version_id BIGINT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT code_context_runs_type_check CHECK (run_type IN ('REPO_INGESTION')),
    CONSTRAINT code_context_runs_status_check CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE UNIQUE INDEX code_context_runs_active_project_unique
    ON code_context_runs(project_id, run_type)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE TABLE repo_inventory_versions (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    provider VARCHAR(30) NOT NULL,
    repository_url VARCHAR(500) NOT NULL,
    branch_name VARCHAR(100) NOT NULL,
    commit_sha VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    repository_profile JSONB NOT NULL,
    tree_summary JSONB NOT NULL,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT repo_inventory_versions_unique UNIQUE (project_id, provider, branch_name, commit_sha),
    CONSTRAINT repo_inventory_versions_provider_check CHECK (provider IN ('GIT')),
    CONSTRAINT repo_inventory_versions_status_check CHECK (status IN ('CURRENT', 'STALE', 'FAILED'))
);

ALTER TABLE code_context_runs
    ADD CONSTRAINT code_context_runs_inventory_fk
        FOREIGN KEY (inventory_version_id) REFERENCES repo_inventory_versions(id);

CREATE TABLE repo_inventory_files (
    id BIGSERIAL PRIMARY KEY,
    inventory_version_id BIGINT NOT NULL REFERENCES repo_inventory_versions(id),
    path TEXT NOT NULL,
    file_type VARCHAR(30) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_hash VARCHAR(128),
    indexed_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT repo_inventory_files_unique UNIQUE (inventory_version_id, path),
    CONSTRAINT repo_inventory_files_type_check CHECK (file_type IN (
        'SOURCE', 'CONFIG', 'MIGRATION', 'TEST', 'DOC', 'BINARY', 'UNKNOWN'
    ))
);

CREATE TABLE code_context_plans (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    workflow_id BIGINT NOT NULL REFERENCES workflows(id),
    inventory_version_id BIGINT NOT NULL REFERENCES repo_inventory_versions(id),
    agent_run_id BIGINT REFERENCES agent_runs(id),
    plan_json JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT code_context_plans_status_check CHECK (status IN ('PROPOSED', 'USED', 'FAILED'))
);

CREATE TABLE code_context_versions (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    provider VARCHAR(30) NOT NULL,
    inventory_version_id BIGINT REFERENCES repo_inventory_versions(id),
    context_plan_id BIGINT REFERENCES code_context_plans(id),
    repository_url VARCHAR(500) NOT NULL,
    branch_name VARCHAR(100) NOT NULL,
    base_commit_sha VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    repository_profile JSONB NOT NULL,
    evidence_json JSONB NOT NULL,
    error_message TEXT,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT code_context_versions_unique UNIQUE (
        project_id, provider, branch_name, base_commit_sha, context_plan_id
    ),
    CONSTRAINT code_context_versions_provider_check CHECK (provider IN ('GIT', 'LOCAL_AGENT')),
    CONSTRAINT code_context_versions_status_check CHECK (status IN ('CURRENT', 'STALE', 'FAILED'))
);

CREATE TABLE code_context_files (
    id BIGSERIAL PRIMARY KEY,
    context_version_id BIGINT NOT NULL REFERENCES code_context_versions(id),
    path TEXT NOT NULL,
    content_hash VARCHAR(128),
    evidence_type VARCHAR(30) NOT NULL,
    summary TEXT NOT NULL,
    important_symbols JSONB NOT NULL,
    excerpt TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT code_context_files_unique UNIQUE (context_version_id, path, evidence_type),
    CONSTRAINT code_context_files_type_check CHECK (evidence_type IN (
        'SOURCE_FILE', 'CONFIG', 'MIGRATION', 'TEST', 'DOC', 'DIFF'
    ))
);

ALTER TABLE agent_runs
    ADD COLUMN code_context_version_id BIGINT REFERENCES code_context_versions(id),
    ADD COLUMN context_plan_id BIGINT REFERENCES code_context_plans(id);

ALTER TABLE agent_runs DROP CONSTRAINT agent_runs_type_check;
ALTER TABLE agent_runs
    ADD CONSTRAINT agent_runs_type_check CHECK (run_type IN (
        'GENERATE_CODE_CONTEXT_PLAN', 'GENERATE_DESIGN', 'GENERATE_SPEC', 'GENERATE_BUILD_PLAN'
    ));

ALTER TABLE document_versions
    ADD COLUMN code_context_version_id BIGINT REFERENCES code_context_versions(id);

ALTER TABLE task_packages
    ADD COLUMN code_context_version_id BIGINT REFERENCES code_context_versions(id),
    ADD COLUMN context_plan_id BIGINT REFERENCES code_context_plans(id);

ALTER TABLE outbox_jobs DROP CONSTRAINT outbox_jobs_type_check;
ALTER TABLE outbox_jobs
    ADD CONSTRAINT outbox_jobs_type_check CHECK (
        job_type IN ('AGENT_RUN', 'CODE_CONTEXT_SYNC', 'GIT_SYNC', 'CI_SYNC')
    );

CREATE INDEX idx_code_context_runs_project_status
    ON code_context_runs(project_id, status, created_at);
CREATE INDEX idx_repo_inventory_versions_project_status
    ON repo_inventory_versions(project_id, status, updated_at);
CREATE INDEX idx_repo_inventory_files_inventory_path
    ON repo_inventory_files(inventory_version_id, path);
CREATE INDEX idx_code_context_versions_project_status
    ON code_context_versions(project_id, status, updated_at);
CREATE INDEX idx_code_context_files_context_path
    ON code_context_files(context_version_id, path);
