CREATE TABLE project_members (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES projects(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    project_role VARCHAR(20) NOT NULL,
    capability_profile JSONB,
    profile_version INTEGER NOT NULL DEFAULT 0,
    profile_completed BOOLEAN NOT NULL DEFAULT FALSE,
    profile_updated_at TIMESTAMPTZ,
    joined_at TIMESTAMPTZ NOT NULL,
    left_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT project_members_project_user_unique UNIQUE (project_id, user_id),
    CONSTRAINT project_members_role_check CHECK (project_role IN ('LEADER', 'MEMBER')),
    CONSTRAINT project_members_status_check CHECK (status IN ('ACTIVE', 'REMOVED'))
);

CREATE TABLE member_profile_versions (
    id BIGSERIAL PRIMARY KEY,
    project_member_id BIGINT NOT NULL REFERENCES project_members(id),
    version_no INTEGER NOT NULL,
    profile JSONB NOT NULL,
    changed_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT member_profile_versions_unique UNIQUE (project_member_id, version_no)
);
