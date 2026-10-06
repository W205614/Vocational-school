CREATE TABLE course_metadata_projection(
 course_id BIGINT PRIMARY KEY,version BIGINT NOT NULL DEFAULT 1,processed_version BIGINT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,
 lease_token CHAR(36),lease_until DATETIME(3),next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),last_error VARCHAR(500),
 KEY ix_metadata_due(status,next_attempt_at,lease_until)
);
