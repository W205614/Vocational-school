CREATE TABLE payment_fact (
 pay_order_no BIGINT PRIMARY KEY,order_id BIGINT NOT NULL,pay_channel VARCHAR(64),paid_at DATETIME(3),
 recorded_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),KEY idx_payment_order(order_id)
) ENGINE=InnoDB;
CREATE TABLE payment_conflict (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,order_id BIGINT NOT NULL,pay_order_no BIGINT NOT NULL,
 reason VARCHAR(64) NOT NULL,status VARCHAR(16) NOT NULL,resolution TEXT,version BIGINT NOT NULL DEFAULT 0,
 resolved_by BIGINT,resolved_at DATETIME(3),created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_conflict_payment(pay_order_no),KEY idx_conflict_status(status,created_at)
) ENGINE=InnoDB;
ALTER TABLE `order` ADD KEY idx_order_user_status_time(user_id,status,create_time);
