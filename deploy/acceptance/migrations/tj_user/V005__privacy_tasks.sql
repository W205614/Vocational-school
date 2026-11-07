-- Disabled-account tombstones use a collision-free internal identifier; public phone input remains 11 digits.
ALTER TABLE user MODIFY cell_phone VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '手机号或已注销账户内部标识';
CREATE TABLE privacy_task (
 id CHAR(36) NOT NULL PRIMARY KEY,
 request_id CHAR(36) NOT NULL,
 user_id BIGINT NOT NULL,
 kind VARCHAR(16) NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL,
 status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 version BIGINT NOT NULL DEFAULT 0,
 lease_token CHAR(36) NULL,
 lease_until DATETIME(3) NULL,
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 error_code VARCHAR(64) NULL,
 policy_version VARCHAR(64) NOT NULL,
 policy_hash CHAR(64) NOT NULL,
 cutoffs JSON NOT NULL,
 plan_hash CHAR(64) NULL,
 approved_by BIGINT NULL,
 artifact_sha256 CHAR(64) NULL,
 artifact_size BIGINT NULL,
 completed_at DATETIME(3) NULL,
 expires_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_privacy_task_key(user_id,idempotency_key),
 KEY ix_privacy_task_due(status,next_attempt_at,lease_until),
 KEY ix_privacy_task_owner(user_id,created_at)
);
CREATE TABLE privacy_task_domain (
 task_id CHAR(36) NOT NULL,
 domain VARCHAR(24) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 inventory JSON NULL,
 dataset_index INT NOT NULL DEFAULT 0,
 page_offset INT NOT NULL DEFAULT 0,
 result JSON NULL,
 PRIMARY KEY(task_id,domain)
);
CREATE TABLE privacy_export_chunk (
 task_id CHAR(36) NOT NULL,
 domain VARCHAR(24) NOT NULL,
 dataset VARCHAR(64) NOT NULL,
 page_offset INT NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 sha256 CHAR(64) NOT NULL,
 bytes INT NOT NULL,
 PRIMARY KEY(task_id,domain,dataset,page_offset)
);
