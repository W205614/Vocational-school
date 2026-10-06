CREATE TABLE IF NOT EXISTS free_enrollment (
 user_id BIGINT NOT NULL, course_id BIGINT NOT NULL, order_id BIGINT NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(user_id,course_id), UNIQUE KEY uq_free_order(order_id)
) ENGINE=InnoDB;
INSERT IGNORE INTO free_enrollment(user_id,course_id,order_id)
SELECT user_id,course_id,MIN(order_id) FROM order_detail WHERE status=5 GROUP BY user_id,course_id;
