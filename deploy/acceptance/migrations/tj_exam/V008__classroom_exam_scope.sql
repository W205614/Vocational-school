ALTER TABLE exam_paper ADD COLUMN classroom_only BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE exam_attempt
    ADD COLUMN class_activity_id BIGINT NULL,
    ADD COLUMN class_member_id BIGINT NULL,
    DROP INDEX uq_exam_active,
    MODIFY COLUMN active_paper_id BIGINT GENERATED ALWAYS AS
        (IF(class_activity_id IS NULL AND status IN ('IN_PROGRESS','WAIT_GRADING'),paper_id,NULL)) STORED,
    ADD COLUMN active_class_member BIGINT GENERATED ALWAYS AS
        (IF(class_activity_id IS NOT NULL AND status IN ('IN_PROGRESS','WAIT_GRADING'),class_member_id,NULL)) STORED,
    ADD UNIQUE KEY uq_exam_active (user_id,active_paper_id),
    ADD UNIQUE KEY uq_class_exam_active (class_activity_id,active_class_member),
    ADD INDEX idx_class_exam_attempt (class_activity_id,class_member_id,created_at);

CREATE TABLE exam_class_start_guard (
    activity_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    PRIMARY KEY (activity_id,member_id)
) ENGINE=InnoDB;
