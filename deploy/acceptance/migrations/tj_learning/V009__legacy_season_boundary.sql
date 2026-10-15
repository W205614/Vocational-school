-- Seasons before the rollout's current natural month remain visible but cannot mint new rewards or honours.
CREATE TABLE IF NOT EXISTS learning_rollout_boundary(id TINYINT PRIMARY KEY,start_month CHAR(6) NOT NULL) ENGINE=InnoDB;
INSERT IGNORE INTO learning_rollout_boundary(id,start_month) VALUES(1,DATE_FORMAT(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00'),'%Y%m'));
INSERT IGNORE INTO learning_season(board_month,name,state,freeze_at,settled_at)
 SELECT DISTINCT board_month,CONCAT(SUBSTRING(board_month,1,4),'年',CAST(SUBSTRING(board_month,5,2) AS UNSIGNED),'月赛季'),'LEGACY_READONLY',
 DATE_ADD(DATE_ADD(STR_TO_DATE(CONCAT(board_month,'01'),'%Y%m%d'),INTERVAL 1 MONTH),INTERVAL 1 DAY),CURRENT_TIMESTAMP(3)
 FROM points_projection WHERE board_month<DATE_FORMAT(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00'),'%Y%m');
UPDATE learning_season SET state='LEGACY_READONLY',settled_at=COALESCE(settled_at,CURRENT_TIMESTAMP(3)),blocked_reason=NULL
 WHERE board_month<DATE_FORMAT(CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00'),'%Y%m');
INSERT IGNORE INTO learning_season_rank(board_month,user_id,rank_no,points,reached_at,display_name)
 SELECT p.board_month,p.user_id,ROW_NUMBER() OVER(PARTITION BY p.board_month ORDER BY p.points DESC,p.reached_at ASC,p.user_id ASC),p.points,p.reached_at,'同学'
 FROM points_projection p JOIN learning_season s ON BINARY s.board_month=BINARY p.board_month WHERE s.state='LEGACY_READONLY' AND p.points>0;
