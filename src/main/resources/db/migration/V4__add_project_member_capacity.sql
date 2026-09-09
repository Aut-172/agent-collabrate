ALTER TABLE project_members
    ADD COLUMN weekly_capacity_points INTEGER,
    ADD COLUMN availability VARCHAR(30),
    ADD CONSTRAINT project_members_weekly_capacity_check
        CHECK (weekly_capacity_points IS NULL OR weekly_capacity_points BETWEEN 1 AND 40);
