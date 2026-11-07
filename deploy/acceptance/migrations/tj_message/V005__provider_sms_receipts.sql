CREATE TABLE sms_delivery_recipient(
    id CHAR(36) PRIMARY KEY,
    task_id CHAR(36) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    provider_code VARCHAR(32),
    provider_template VARCHAR(64),
    sign_name VARCHAR(100),
    out_id CHAR(36),
    biz_id VARCHAR(128),
    request_id VARCHAR(128),
    submitted_at DATETIME(3),
    expires_at DATETIME(3),
    receipt_deadline DATETIME(3),
    attempts INT NOT NULL DEFAULT 0,
    lease_token CHAR(36),
    lease_until DATETIME(3),
    next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_error VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_sms_recipient(task_id,phone),
    KEY ix_sms_recipient_due(state,next_attempt_at),
    KEY ix_sms_recipient_task(task_id)
);
ALTER TABLE sms_delivery_task ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
-- Old real submissions have no correlation key and cannot be safely resent or called delivered.
UPDATE sms_delivery_task t SET t.status='UNKNOWN',t.last_error='LEGACY_SUBMISSION_RESULT_UNVERIFIED'
WHERE t.status IN('PENDING','DONE') AND NOT EXISTS(SELECT 1 FROM simulated_sms s WHERE s.task_id=t.id);
UPDATE sms_delivery_task t JOIN simulated_sms s ON s.task_id=t.id SET t.status='DONE' WHERE t.status='PENDING';
