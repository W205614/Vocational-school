CREATE TABLE learning_season (
 board_month CHAR(6) PRIMARY KEY, name VARCHAR(64) NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'OPEN', freeze_at DATETIME(3) NOT NULL,
 settled_at DATETIME(3) NULL, blocked_reason VARCHAR(500) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;
ALTER TABLE points_projection ADD COLUMN reached_at DATETIME(3) NULL;
UPDATE points_projection p LEFT JOIN (SELECT user_id,DATE_FORMAT(create_time,'%Y%m') m,MAX(create_time) t FROM points_record GROUP BY user_id,DATE_FORMAT(create_time,'%Y%m')) r
 ON p.user_id=r.user_id AND p.board_month=r.m SET p.reached_at=COALESCE(r.t,CURRENT_TIMESTAMP(3));
ALTER TABLE points_record ADD COLUMN occurred_at DATETIME(3) NULL, ADD COLUMN board_month CHAR(6) NULL,ADD COLUMN late BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE points_record SET occurred_at=create_time,board_month=DATE_FORMAT(create_time,'%Y%m');
CREATE TABLE learning_season_rank (
 board_month CHAR(6) NOT NULL,user_id BIGINT NOT NULL,rank_no INT NOT NULL,points INT NOT NULL,reached_at DATETIME(3) NOT NULL,
 display_name VARCHAR(255) NULL,PRIMARY KEY(board_month,user_id),UNIQUE KEY uq_season_rank(board_month,rank_no)
) ENGINE=InnoDB;
CREATE TABLE learning_points_reconciliation (
 id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,board_month CHAR(6) NOT NULL,event_key VARCHAR(128) NOT NULL,
 type INT NOT NULL,points INT NOT NULL,occurred_at DATETIME(3) NOT NULL,status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),UNIQUE KEY uq_late_event(type,event_key)
) ENGINE=InnoDB;
CREATE TABLE learning_milestone (
 user_id BIGINT NOT NULL,course_id BIGINT NOT NULL,section_id BIGINT NOT NULL,kind VARCHAR(16) NOT NULL,completed_at DATETIME(3) NOT NULL,
 PRIMARY KEY(user_id,course_id,section_id),KEY idx_milestone_day(user_id,completed_at)
) ENGINE=InnoDB;
CREATE TABLE learning_achievement (
 user_id BIGINT NOT NULL,code VARCHAR(48) NOT NULL,season_month CHAR(6) NOT NULL DEFAULT '',name VARCHAR(100) NOT NULL,
 awarded_at DATETIME(3) NOT NULL,PRIMARY KEY(user_id,code,season_month)
) ENGINE=InnoDB;
ALTER TABLE interaction_question ADD COLUMN accepted_reply_id BIGINT NULL,ADD COLUMN accepted_at DATETIME(3) NULL,ADD COLUMN accepted_by BIGINT NULL;
CREATE TABLE learning_reward_rule (
 id BIGINT PRIMARY KEY,board_month CHAR(6) NOT NULL,rank_from INT NOT NULL,rank_to INT NOT NULL,coupon_id BIGINT NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,version BIGINT NOT NULL DEFAULT 0,created_by BIGINT NOT NULL,updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_reward_month(board_month,rank_from)
) ENGINE=InnoDB;
CREATE TABLE learning_reward_request (
 request_id VARCHAR(64) PRIMARY KEY,board_month CHAR(6) NOT NULL,user_id BIGINT NOT NULL,rank_no INT NOT NULL,rule_id BIGINT NOT NULL,coupon_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',user_coupon_id BIGINT NULL,attempts INT NOT NULL DEFAULT 1,last_error VARCHAR(500) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uq_season_reward(board_month,user_id,rule_id)
) ENGINE=InnoDB;
