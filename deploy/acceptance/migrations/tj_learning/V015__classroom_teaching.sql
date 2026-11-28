CREATE TABLE teaching_class (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    registration_opens DATETIME(3) NOT NULL,
    registration_closes DATETIME(3) NOT NULL,
    starts_at DATETIME(3) NOT NULL,
    ends_at DATETIME(3) NOT NULL,
    capacity INT NOT NULL,
    pass_percent INT NOT NULL,
    INDEX idx_class_course (course_id, state, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_staff (
    class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    duty VARCHAR(12) NOT NULL,
    PRIMARY KEY (class_id, user_id),
    INDEX idx_class_staff_user (user_id, class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    source_order_id BIGINT NULL,
    state VARCHAR(16) NOT NULL,
    joined_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ended_at DATETIME(3) NULL,
    active_course_id BIGINT GENERATED ALWAYS AS
        (IF(state IN ('ACTIVE','HELD'), course_id, NULL)) STORED,
    UNIQUE KEY uq_active_class_course (user_id, active_course_id),
    UNIQUE KEY uq_class_enrollment_order (order_id),
    INDEX idx_class_member_source_order (source_order_id, state),
    INDEX idx_class_member_class (class_id, state, id),
    INDEX idx_class_member_student (user_id, class_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    actor_id BIGINT NULL,
    student_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    detail VARCHAR(500) NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_class_event (class_id, id),
    INDEX idx_class_event_student (student_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_activity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    section_id BIGINT NULL,
    paper_id BIGINT NULL,
    title VARCHAR(120) NOT NULL,
    scheduled_at DATETIME(3) NOT NULL,
    closes_at DATETIME(3) NULL,
    weight INT NOT NULL DEFAULT 0,
    max_attempts INT NULL,
    current_version INT NOT NULL DEFAULT 1,
    INDEX idx_class_schedule (class_id, scheduled_at, id),
    INDEX idx_class_paper (paper_id, class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_activity_version (
    activity_id BIGINT NOT NULL,
    version INT NOT NULL,
    title VARCHAR(120) NOT NULL,
    instructions TEXT NOT NULL,
    reason VARCHAR(500) NULL,
    changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (activity_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_extension (
    activity_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    closes_at DATETIME(3) NOT NULL,
    PRIMARY KEY (activity_id, member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_paper_guard (
    paper_id BIGINT NOT NULL PRIMARY KEY,
    revealed_at DATETIME(3) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_submission (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    request_key CHAR(36) NOT NULL,
    assignment_version INT NOT NULL,
    revision INT NOT NULL,
    description TEXT NOT NULL,
    work_url VARCHAR(2048) NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    score INT NULL,
    feedback TEXT NULL,
    reviewer_id BIGINT NULL,
    submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    cleaned_at DATETIME(3) NULL,
    UNIQUE KEY uq_class_submission_request (activity_id, member_id, request_key),
    UNIQUE KEY uq_class_submission_revision (activity_id, member_id, revision),
    INDEX idx_class_submission_student (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_grade (
    member_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score INT NOT NULL,
    feedback TEXT NULL,
    published_by BIGINT NOT NULL,
    published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (member_id, activity_id),
    INDEX idx_class_grade_student (user_id, activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_grade_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    old_score INT NOT NULL,
    new_score INT NOT NULL,
    old_feedback TEXT NULL,
    reason VARCHAR(500) NOT NULL,
    changed_by BIGINT NOT NULL,
    changed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_class_grade_history_student (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_result (
    member_id BIGINT NOT NULL PRIMARY KEY,
    class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    total_percent DECIMAL(5,2) NOT NULL,
    passed BOOLEAN NOT NULL,
    finalized_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_class_result_student (user_id, class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teaching_class_discussion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    section_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NULL,
    body TEXT NOT NULL,
    staff_reply BOOLEAN NOT NULL DEFAULT FALSE,
    posted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    cleaned_at DATETIME(3) NULL,
    INDEX idx_class_discussion (class_id, parent_id, id),
    INDEX idx_class_discussion_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
