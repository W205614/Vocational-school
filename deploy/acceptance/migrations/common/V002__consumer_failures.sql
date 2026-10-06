CREATE TABLE IF NOT EXISTS reliability_consumer_failure(
 failure_id CHAR(36) PRIMARY KEY,event_id VARCHAR(128) NOT NULL,queue_name VARCHAR(255) NOT NULL,
 body LONGBLOB NOT NULL,content_type VARCHAR(128),status VARCHAR(16) NOT NULL DEFAULT 'FAILED',
 attempts INT NOT NULL DEFAULT 0,last_error VARCHAR(1000),version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_failure_event(queue_name,event_id),KEY ix_failure_status(status,created_at)
);
