CREATE TABLE IF NOT EXISTS reliability_outbox (
 event_id VARCHAR(64) PRIMARY KEY,
 business_key VARCHAR(190) NOT NULL,
 exchange_name VARCHAR(128) NOT NULL,
 routing_key VARCHAR(128) NOT NULL,
 event_type VARCHAR(128) NOT NULL,
 schema_version INT NOT NULL DEFAULT 1,
 payload LONGTEXT NOT NULL,
 delay_ms BIGINT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 lease_token VARCHAR(64),
 next_attempt_at DATETIME(3) NOT NULL,
 last_error VARCHAR(1000),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 sent_at DATETIME(3),
 UNIQUE KEY uq_outbox_business_event (business_key, event_type),
 KEY idx_outbox_dispatch (status,next_attempt_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS reliability_inbox (
 consumer_name VARCHAR(128) NOT NULL,
 event_id VARCHAR(64) NOT NULL,
 processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(consumer_name,event_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS reliability_operation (
 operation_id VARCHAR(64) PRIMARY KEY,
 user_id BIGINT NOT NULL,
 kind VARCHAR(32) NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 payload LONGTEXT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 result LONGTEXT,
 error_code VARCHAR(64),
 error_message VARCHAR(1000),
 attempts INT NOT NULL DEFAULT 0,
 lease_token VARCHAR(64),
 next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_operation_request (user_id,kind,idempotency_key),
 KEY idx_operation_dispatch (kind,status,next_attempt_at)
) ENGINE=InnoDB;
