CREATE TABLE IF NOT EXISTS admin_audit (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT NOT NULL, actor_role BIGINT NOT NULL,
 method VARCHAR(8) NOT NULL, object_path VARCHAR(512) NOT NULL,
 request_id VARCHAR(80) NOT NULL, result VARCHAR(24) NOT NULL,
 http_status INT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 finished_at DATETIME(3) NULL,
 KEY ix_audit_created(created_at,id), KEY ix_audit_actor(actor_id,created_at), KEY ix_audit_request(request_id)
);
