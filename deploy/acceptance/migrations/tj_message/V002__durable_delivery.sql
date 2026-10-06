CREATE TABLE sms_delivery_task(id CHAR(36) PRIMARY KEY,payload JSON NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,lease_token CHAR(36),next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),last_error VARCHAR(1000),KEY ix_sms_due(status,next_attempt_at));
CREATE TABLE simulated_sms(id BIGINT AUTO_INCREMENT PRIMARY KEY,task_id CHAR(36) NOT NULL UNIQUE,payload JSON NOT NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3));
ALTER TABLE user_inbox ADD COLUMN public_notice_id BIGINT,ADD UNIQUE KEY uq_user_public_notice(user_id,public_notice_id);
ALTER TABLE notice_task ADD KEY ix_notice_due(finished,push_time);
