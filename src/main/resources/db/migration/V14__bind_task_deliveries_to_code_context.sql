ALTER TABLE task_deliveries
    ADD COLUMN code_context_version_id BIGINT REFERENCES code_context_versions(id),
    ADD COLUMN context_plan_id BIGINT REFERENCES code_context_plans(id),
    ADD COLUMN base_commit_sha VARCHAR(100);

