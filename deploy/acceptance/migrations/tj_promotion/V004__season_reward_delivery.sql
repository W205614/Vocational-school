CREATE TABLE season_reward_delivery(
 request_id VARCHAR(128) PRIMARY KEY,user_id BIGINT NOT NULL,coupon_id BIGINT NOT NULL,
 season_month CHAR(6) NOT NULL,rule_id BIGINT NOT NULL,status VARCHAR(20) NOT NULL,
 user_coupon_id BIGINT NULL,last_event_id VARCHAR(64) NOT NULL,last_error VARCHAR(200) NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_season_reward(season_month,user_id,rule_id)
) ENGINE=InnoDB;
