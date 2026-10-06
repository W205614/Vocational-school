ALTER TABLE points_record ADD COLUMN source_event_id VARCHAR(64) NULL,
 ADD UNIQUE KEY uq_points_event(type,source_event_id),ADD KEY idx_points_user_time_type(user_id,create_time,type);
CREATE TABLE points_daily_quota(user_id BIGINT NOT NULL,type INT NOT NULL,quota_day DATE NOT NULL,points INT NOT NULL DEFAULT 0,
 PRIMARY KEY(user_id,type,quota_day)) ENGINE=InnoDB;
INSERT INTO points_daily_quota SELECT user_id,type,DATE(create_time),SUM(points) FROM points_record GROUP BY user_id,type,DATE(create_time);
CREATE TABLE points_projection(board_month CHAR(6) NOT NULL,user_id BIGINT NOT NULL,points INT NOT NULL,version BIGINT NOT NULL,processed_version BIGINT NOT NULL,
 PRIMARY KEY(board_month,user_id),KEY idx_points_dirty(processed_version,version)) ENGINE=InnoDB;
INSERT INTO points_projection SELECT DATE_FORMAT(create_time,'%Y%m'),user_id,SUM(points),1,0 FROM points_record GROUP BY DATE_FORMAT(create_time,'%Y%m'),user_id;
CREATE TABLE sign_record(user_id BIGINT NOT NULL,sign_day DATE NOT NULL,sign_days INT NOT NULL,reward_points INT NOT NULL,
 PRIMARY KEY(user_id,sign_day)) ENGINE=InnoDB;
