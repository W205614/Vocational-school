CREATE TABLE exam_review_state (
 attempt_id BIGINT NOT NULL, question_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 mastered TINYINT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
 last_reviewed_at DATETIME(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(attempt_id,question_id), KEY idx_review_user(user_id,mastered,updated_at)
) ENGINE=InnoDB;
CREATE TABLE exam_review_record (
 id BIGINT NOT NULL PRIMARY KEY, operation_id VARCHAR(36) NOT NULL,
 attempt_id BIGINT NOT NULL, question_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 response TEXT NOT NULL, correct TINYINT, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_review_operation(operation_id), KEY idx_review_history(user_id,attempt_id,question_id,created_at)
) ENGINE=InnoDB;
