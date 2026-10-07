CREATE TABLE auth_session(
 session_id CHAR(36) PRIMARY KEY,user_id BIGINT NOT NULL,role_id BIGINT NOT NULL,auth_version BIGINT NOT NULL,
 audience VARCHAR(16) NOT NULL,current_jti CHAR(64) NOT NULL,rotation_jti CHAR(36) NOT NULL,previous_jti CHAR(64),previous_until DATETIME(3),
 expires_at DATETIME(3) NOT NULL,revoked_at DATETIME(3),created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 last_seen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),KEY ix_session_owner(user_id,revoked_at,expires_at));
CREATE TABLE auth_login_failure(id BIGINT AUTO_INCREMENT PRIMARY KEY,account_hash CHAR(64) NOT NULL,ip_hash CHAR(64) NOT NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),KEY ix_login_failure_time(created_at));
