CREATE TABLE document_decisions (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL REFERENCES workflows(id),
    document_version_id BIGINT NOT NULL REFERENCES document_versions(id),
    document_type VARCHAR(30) NOT NULL,
    decision_key VARCHAR(20) NOT NULL,
    question TEXT NOT NULL,
    options_json JSONB NOT NULL,
    recommended_option VARCHAR(30) NOT NULL,
    unresolved_impact TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    selected_option VARCHAR(30),
    resolved_by BIGINT REFERENCES users(id),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT document_decisions_version_key_unique UNIQUE (document_version_id, decision_key),
    CONSTRAINT document_decisions_type_check CHECK (document_type IN ('DESIGN', 'SPEC')),
    CONSTRAINT document_decisions_status_check CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT document_decisions_resolution_check CHECK (
        (status = 'OPEN' AND selected_option IS NULL AND resolved_by IS NULL AND resolved_at IS NULL)
        OR (status = 'RESOLVED' AND selected_option IS NOT NULL AND resolved_by IS NOT NULL AND resolved_at IS NOT NULL)
    )
);

CREATE INDEX idx_document_decisions_workflow ON document_decisions(workflow_id, document_type, document_version_id);
CREATE INDEX idx_document_decisions_open ON document_decisions(document_version_id, status);
