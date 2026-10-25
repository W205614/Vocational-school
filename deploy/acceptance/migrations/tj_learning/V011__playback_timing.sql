ALTER TABLE learning_record
 ADD COLUMN watched_millis BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN credited_millis BIGINT NOT NULL DEFAULT 0,
 ADD COLUMN completion_source VARCHAR(16) DEFAULT NULL;
UPDATE learning_record SET completion_source='LEGACY',update_time=update_time WHERE finished=1;
CREATE TABLE learning_playback_session (
 user_id BIGINT PRIMARY KEY,
 session_id CHAR(36) NOT NULL,
 lesson_id BIGINT NOT NULL,
 course_id BIGINT NOT NULL,
 section_id BIGINT NOT NULL,
 duration_millis BIGINT NOT NULL,
 section_count INT NOT NULL,
 last_sequence BIGINT NOT NULL DEFAULT 0,
 last_position_millis BIGINT NOT NULL DEFAULT 0,
 last_rate DECIMAL(3,2) NOT NULL DEFAULT 1,
 last_state VARCHAR(16) NOT NULL DEFAULT 'PAUSED',
 last_seen_at DATETIME(3) NOT NULL,
 last_request_hash CHAR(64),
 last_response LONGTEXT,
 ended TINYINT NOT NULL DEFAULT 0,
 UNIQUE KEY uq_playback_session(session_id)
) ENGINE=InnoDB;
