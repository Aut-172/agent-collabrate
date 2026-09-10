ALTER TABLE git_operations
    ADD COLUMN verified_commit_sha VARCHAR(100),
    ADD COLUMN pull_request_head_sha VARCHAR(100),
    ADD COLUMN verified_at TIMESTAMPTZ;

