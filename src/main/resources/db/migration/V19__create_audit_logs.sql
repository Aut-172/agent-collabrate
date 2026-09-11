CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    project_id BIGINT REFERENCES projects(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    request_id VARCHAR(100),
    details_json JSONB,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_logs_project_time ON audit_logs(project_id, created_at DESC, id DESC);
CREATE INDEX idx_audit_logs_entity_time ON audit_logs(entity_type, entity_id, created_at DESC, id DESC);
