ALTER TABLE liked_record ADD UNIQUE KEY uq_like_relation(user_id,biz_type,biz_id);
CREATE TABLE liked_counter (biz_type VARCHAR(32) NOT NULL,biz_id BIGINT NOT NULL,liked_times INT NOT NULL,version BIGINT NOT NULL,
 PRIMARY KEY(biz_type,biz_id)) ENGINE=InnoDB;
INSERT INTO liked_counter SELECT biz_type,biz_id,COUNT(*),1 FROM liked_record GROUP BY biz_type,biz_id;
