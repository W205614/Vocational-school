CREATE TABLE privacy_domain_execution (
 request_id CHAR(36) NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL,
 policy_version VARCHAR(64) NOT NULL,
 request_hash CHAR(64) NOT NULL,
 result JSON NULL,
 completed_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY ix_privacy_execution_user(user_id,created_at)
);
