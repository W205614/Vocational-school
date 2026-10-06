CREATE TABLE refund_delivery_task(refund_id BIGINT PRIMARY KEY,status VARCHAR(16) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,lease_token CHAR(36),next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),last_error VARCHAR(1000),version BIGINT NOT NULL DEFAULT 0,KEY ix_refund_delivery(status,next_attempt_at));
INSERT INTO refund_delivery_task(refund_id) SELECT id FROM refund_apply WHERE status=3;
CREATE TABLE payment_reconcile(order_id BIGINT PRIMARY KEY,status VARCHAR(16) NOT NULL DEFAULT 'PENDING',attempts INT NOT NULL DEFAULT 0,lease_token CHAR(36),next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),expires_at DATETIME(3) NOT NULL,last_error VARCHAR(1000),version BIGINT NOT NULL DEFAULT 0,KEY ix_payment_reconcile(status,next_attempt_at));
INSERT INTO payment_reconcile(order_id,expires_at) SELECT id,DATE_ADD(create_time,INTERVAL 1 DAY) FROM `order` WHERE status=1;
ALTER TABLE refund_apply ADD COLUMN active_detail_id BIGINT GENERATED ALWAYS AS(IF(status IN(1,3),order_detail_id,NULL)) STORED,
 ADD UNIQUE KEY uq_active_refund(active_detail_id),ADD KEY ix_refund_user_time(user_id,create_time,id);
