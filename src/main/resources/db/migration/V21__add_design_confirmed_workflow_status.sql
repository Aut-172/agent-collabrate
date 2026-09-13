ALTER TABLE workflows
    DROP CONSTRAINT workflows_status_check;

UPDATE workflows w
SET status = 'DESIGN_CONFIRMED', updated_at = CURRENT_TIMESTAMP
WHERE w.status = 'DESIGN_PROPOSED'
  AND EXISTS (
      SELECT 1
      FROM document_versions d
      WHERE d.workflow_id = w.id
        AND d.document_type = 'DESIGN'
        AND d.version_no = (
            SELECT MAX(latest.version_no)
            FROM document_versions latest
            WHERE latest.workflow_id = w.id
              AND latest.document_type = 'DESIGN'
        )
        AND d.is_confirmed = TRUE
  );

ALTER TABLE workflows
    ADD CONSTRAINT workflows_status_check CHECK (status IN (
        'INTENT', 'DESIGN_PROPOSED', 'DESIGN_CONFIRMED', 'SPEC_PROPOSED', 'SPEC_CONFIRMED',
        'BUILD_PLAN_PROPOSED', 'PLAN_APPROVED', 'TASKS_READY', 'IN_PROGRESS',
        'DELIVERY_SUBMITTED', 'CI_RUNNING', 'CI_PASSED', 'READY_TO_CLOSE',
        'DONE', 'CANCELLED', 'FAILED'
    ));
