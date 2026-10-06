ALTER TABLE user_coupon ADD COLUMN claim_operation_id VARCHAR(64) NULL, ADD COLUMN reserved_order_id BIGINT NULL,
 ADD UNIQUE KEY uq_coupon_claim_operation(claim_operation_id),
 ADD KEY idx_coupon_user_status_time(user_id,status,term_end_time),
 ADD KEY idx_coupon_reservation(reserved_order_id);
CREATE TABLE coupon_reservation (
 order_id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, coupon_ids TEXT NOT NULL,
 request_hash CHAR(64) NOT NULL, status VARCHAR(16) NOT NULL,
 expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_reservation_expiry(status,expires_at)
) ENGINE=InnoDB;
