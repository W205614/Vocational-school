ALTER TABLE course_content
    ADD COLUMN learning_outcomes VARCHAR(500) NULL,
    ADD COLUMN prerequisites VARCHAR(500) NULL,
    ADD COLUMN difficulty VARCHAR(20) NULL,
    ADD COLUMN practice_overview VARCHAR(500) NULL,
    ADD COLUMN handout TEXT NULL;

ALTER TABLE course_content_draft
    ADD COLUMN learning_outcomes VARCHAR(500) NULL,
    ADD COLUMN prerequisites VARCHAR(500) NULL,
    ADD COLUMN difficulty VARCHAR(20) NULL,
    ADD COLUMN practice_overview VARCHAR(500) NULL,
    ADD COLUMN handout TEXT NULL;
