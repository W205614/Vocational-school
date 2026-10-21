-- Synthetic bootstraps may already record V002 while leaving this singleton empty.
-- Repair forward without replaying V002 or modifying existing permission assignments.
CREATE TABLE IF NOT EXISTS auth_cache_guard (id INT PRIMARY KEY);
INSERT IGNORE INTO auth_cache_guard (id) VALUES (1);
