CREATE TABLE learning_practice_assignment (
    course_id BIGINT NOT NULL PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    instructions TEXT NOT NULL,
    published_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE learning_practice_submission (
    id BIGINT NOT NULL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    revision INT NOT NULL,
    submission_key VARCHAR(36) NOT NULL,
    description TEXT NOT NULL,
    work_url VARCHAR(2048) NULL,
    status VARCHAR(24) NOT NULL,
    feedback TEXT NULL,
    submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewed_at DATETIME(3) NULL,
    cleaned_at DATETIME(3) NULL,
    UNIQUE KEY uk_practice_revision (course_id,user_id,revision),
    UNIQUE KEY uk_practice_request (course_id,user_id,submission_key),
    KEY idx_practice_user (user_id,course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
