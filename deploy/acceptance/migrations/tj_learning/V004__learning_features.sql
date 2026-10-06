ALTER TABLE interaction_reply ADD COLUMN liked_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE course_favorite (user_id BIGINT NOT NULL,course_id BIGINT NOT NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(user_id,course_id),KEY idx_favorite_time(user_id,created_at)) ENGINE=InnoDB;
CREATE TABLE course_note (
 id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,course_id BIGINT NOT NULL,section_id BIGINT,moment INT,
 content TEXT NOT NULL,version BIGINT NOT NULL DEFAULT 0,deleted TINYINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 KEY idx_note_user_course(user_id,deleted,course_id,updated_at)
) ENGINE=InnoDB;
