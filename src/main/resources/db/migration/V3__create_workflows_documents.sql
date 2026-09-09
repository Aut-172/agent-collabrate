CREATE TABLE workflows (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    intent_level VARCHAR(20) NOT NULL,
    parent_workflow_id BIGINT,
    status VARCHAR(40) NOT NULL,
    health VARCHAR(30) NOT NULL DEFAULT 'HEALTHY',
    created_by BIGINT NOT NULL REFERENCES users(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT workflows_status_check CHECK (status IN (
        'INTENT', 'DESIGN_PROPOSED', 'SPEC_PROPOSED', 'SPEC_CONFIRMED',
        'BUILD_PLAN_PROPOSED', 'PLAN_APPROVED', 'TASKS_READY', 'IN_PROGRESS',
        'DELIVERY_SUBMITTED', 'CI_RUNNING', 'CI_PASSED', 'READY_TO_CLOSE',
        'DONE', 'CANCELLED', 'FAILED'
    )),
    CONSTRAINT workflows_id_project_unique UNIQUE (id, project_id),
    CONSTRAINT workflows_parent_same_project_fk FOREIGN KEY (parent_workflow_id, project_id)
        REFERENCES workflows(id, project_id),
    CONSTRAINT workflows_intent_level_check CHECK (intent_level IN ('ARCHITECTURE', 'FEATURE', 'CHANGE')),
    CONSTRAINT workflows_health_check CHECK (health IN ('HEALTHY', 'NEEDS_ATTENTION'))
);

CREATE TABLE workflow_members (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    member_role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT workflow_members_unique UNIQUE (workflow_id, user_id),
    CONSTRAINT workflow_members_role_check CHECK (member_role IN ('OWNER', 'PARTICIPANT'))
);

CREATE TABLE document_versions (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows(id),
    document_type VARCHAR(30) NOT NULL,
    version_no INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_format VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    created_by BIGINT REFERENCES users(id),
    agent_run_id BIGINT,
    is_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    confirmed_by BIGINT REFERENCES users(id),
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT document_versions_unique UNIQUE (workflow_id, document_type, version_no),
    CONSTRAINT document_versions_type_check CHECK (document_type IN ('DESIGN', 'SPEC', 'BUILD_PLAN')),
    CONSTRAINT document_versions_format_check CHECK (content_format IN ('MARKDOWN', 'JSON')),
    CONSTRAINT document_versions_source_check CHECK (source IN ('AGENT', 'USER')),
    CONSTRAINT document_versions_source_actor_check CHECK (
        (source = 'USER' AND created_by IS NOT NULL AND agent_run_id IS NULL)
        OR (source = 'AGENT' AND created_by IS NULL AND agent_run_id IS NOT NULL)
    ),
    CONSTRAINT document_versions_confirmation_check CHECK (
        (is_confirmed = FALSE AND confirmed_by IS NULL AND confirmed_at IS NULL)
        OR (is_confirmed = TRUE AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
    )
);
