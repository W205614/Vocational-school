CREATE TABLE learning_entitlement(
 order_detail_id BIGINT PRIMARY KEY,order_id BIGINT NOT NULL,user_id BIGINT NOT NULL,course_id BIGINT NOT NULL,
 active TINYINT NOT NULL,expires_at DATETIME(3),created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_entitlement_user_course(user_id,course_id,active,expires_at),KEY idx_entitlement_order(order_id)
) ENGINE=InnoDB;
INSERT INTO learning_entitlement(order_detail_id,order_id,user_id,course_id,active,expires_at)
 SELECT d.id,d.order_id,d.user_id,d.course_id,1,l.expire_time FROM tj_trade.order_detail d
 JOIN learning_lesson l ON l.user_id=d.user_id AND l.course_id=d.course_id WHERE d.status IN(2,4,5);
