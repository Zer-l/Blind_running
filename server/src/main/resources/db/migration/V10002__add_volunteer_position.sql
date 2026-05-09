ALTER TABLE run_requests
    ADD COLUMN volunteer_lat              DOUBLE       NULL,
    ADD COLUMN volunteer_lng              DOUBLE       NULL,
    ADD COLUMN volunteer_position_updated_at DATETIME(6) NULL;
