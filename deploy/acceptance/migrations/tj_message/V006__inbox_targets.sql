ALTER TABLE user_inbox
    ADD COLUMN target_type VARCHAR(24) NULL,
    ADD COLUMN target_id BIGINT NULL,
    ADD COLUMN course_id BIGINT NULL,
    ADD COLUMN section_id BIGINT NULL;
