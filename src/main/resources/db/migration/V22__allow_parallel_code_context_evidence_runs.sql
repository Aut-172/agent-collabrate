-- Repository ingestion remains single-flight per project. Evidence collection is
-- scoped to a Context Plan, so different Workflows can collect evidence in parallel
-- without colliding on the old project-wide active-run index.
DROP INDEX IF EXISTS code_context_runs_active_project_unique;

CREATE UNIQUE INDEX code_context_runs_active_ingestion_unique
    ON code_context_runs(project_id, run_type)
    WHERE run_type = 'REPO_INGESTION' AND status IN ('QUEUED', 'RUNNING');

CREATE UNIQUE INDEX code_context_runs_active_evidence_plan_unique
    ON code_context_runs(project_id, run_type, context_plan_id)
    WHERE run_type = 'EVIDENCE_COLLECTION'
      AND status IN ('QUEUED', 'RUNNING');
