ALTER TABLE projects
    ADD COLUMN ci_status VARCHAR(30) NOT NULL DEFAULT 'CI_NOT_CONFIGURED';

ALTER TABLE projects
    ADD CONSTRAINT projects_ci_status_check
        CHECK (ci_status IN ('CI_NOT_CONFIGURED', 'CI_REQUIRED'));

ALTER TABLE workflows
    ADD COLUMN completion_mode VARCHAR(30);

UPDATE workflows
SET completion_mode = CASE
    WHEN intent_level = 'ARCHITECTURE' THEN 'ARCHITECTURE_BASELINE'
    ELSE 'CI_REQUIRED'
END;

ALTER TABLE workflows
    ALTER COLUMN completion_mode SET NOT NULL,
    ADD CONSTRAINT workflows_completion_mode_check
        CHECK (completion_mode IN ('ARCHITECTURE_BASELINE', 'CI_BOOTSTRAP', 'CI_REQUIRED')),
    ADD CONSTRAINT workflows_intent_completion_mode_check
        CHECK (
            (intent_level = 'ARCHITECTURE' AND completion_mode = 'ARCHITECTURE_BASELINE')
            OR (intent_level IN ('FEATURE', 'CHANGE')
                AND completion_mode IN ('CI_BOOTSTRAP', 'CI_REQUIRED'))
        );

CREATE UNIQUE INDEX uq_workflows_active_ci_bootstrap
    ON workflows(project_id)
    WHERE completion_mode = 'CI_BOOTSTRAP'
      AND status NOT IN ('DONE', 'CANCELLED', 'FAILED');
