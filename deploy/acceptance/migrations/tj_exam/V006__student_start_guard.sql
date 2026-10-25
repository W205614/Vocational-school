CREATE TABLE IF NOT EXISTS exam_start_guard (
 user_id BIGINT NOT NULL,
 paper_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,paper_id)
) ENGINE=InnoDB;
