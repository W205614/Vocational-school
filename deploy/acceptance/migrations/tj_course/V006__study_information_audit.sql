CREATE TABLE course_study_information_change (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    field VARCHAR(32) NOT NULL,
    previous_value VARCHAR(500),
    new_value VARCHAR(500),
    operator_id BIGINT NOT NULL,
    changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY ix_study_information_change_course (course_id, changed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
