ALTER TABLE workflows
    ADD COLUMN pull_request_required BOOLEAN NOT NULL DEFAULT TRUE;
