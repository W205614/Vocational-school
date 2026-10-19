-- Match live-board ordering; names are a separate, retryable snapshot enrichment.
ALTER TABLE points_projection
 ADD KEY idx_points_month_order(board_month,points DESC,reached_at ASC,user_id ASC);
ALTER TABLE learning_season_rank
 ADD COLUMN name_resolved_at DATETIME(3) NULL,
 ADD COLUMN name_next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 ADD KEY idx_season_name_pending(name_resolved_at,name_next_attempt_at,board_month,user_id);
-- Previously frozen names remain immutable, including the historical fallback name.
UPDATE learning_season_rank SET name_resolved_at=CURRENT_TIMESTAMP(3) WHERE display_name IS NOT NULL;
