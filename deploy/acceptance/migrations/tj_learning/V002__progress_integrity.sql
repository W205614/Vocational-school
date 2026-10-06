ALTER TABLE learning_record ADD UNIQUE KEY uq_learning_record_section(lesson_id,section_id);
CREATE TABLE learning_progress_pending (
 record_id BIGINT PRIMARY KEY,lesson_id BIGINT NOT NULL,section_id BIGINT NOT NULL,moment INT NOT NULL,
 version BIGINT NOT NULL,processed_version BIGINT NOT NULL DEFAULT 0,updated_at DATETIME(3) NOT NULL,
 KEY idx_progress_dirty(updated_at,processed_version,version)
) ENGINE=InnoDB;
