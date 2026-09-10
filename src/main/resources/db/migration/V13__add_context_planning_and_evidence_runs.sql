ALTER TABLE code_context_runs DROP CONSTRAINT code_context_runs_type_check;
ALTER TABLE code_context_runs
    ADD CONSTRAINT code_context_runs_type_check CHECK (
        run_type IN ('REPO_INGESTION', 'EVIDENCE_COLLECTION')
    );

ALTER TABLE code_context_runs
    ADD COLUMN context_plan_id BIGINT REFERENCES code_context_plans(id),
    ADD COLUMN code_context_version_id BIGINT REFERENCES code_context_versions(id);

ALTER TABLE agent_runs
    ADD COLUMN inventory_version_id BIGINT REFERENCES repo_inventory_versions(id);

ALTER TABLE outbox_jobs DROP CONSTRAINT outbox_jobs_type_check;
ALTER TABLE outbox_jobs
    ADD CONSTRAINT outbox_jobs_type_check CHECK (
        job_type IN ('AGENT_RUN', 'CODE_CONTEXT_SYNC', 'CODE_CONTEXT_EVIDENCE', 'GIT_SYNC', 'CI_SYNC')
    );
