CREATE TABLE course_sale_detail(order_detail_id BIGINT PRIMARY KEY,course_id BIGINT NOT NULL,active TINYINT NOT NULL,
 KEY idx_sale_course(course_id,active)) ENGINE=InnoDB;
CREATE TABLE course_sales_projection(course_id BIGINT PRIMARY KEY,sold BIGINT NOT NULL,version BIGINT NOT NULL,processed_version BIGINT NOT NULL) ENGINE=InnoDB;
INSERT INTO course_sale_detail SELECT id,course_id,1 FROM tj_trade.order_detail WHERE status IN(2,4,5);
INSERT INTO course_sales_projection SELECT course_id,COUNT(*),1,0 FROM course_sale_detail WHERE active=1 GROUP BY course_id;
