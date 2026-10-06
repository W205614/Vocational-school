CREATE TABLE IF NOT EXISTS auth_cache_guard (id INT PRIMARY KEY);
INSERT IGNORE INTO auth_cache_guard VALUES(1);
CREATE UNIQUE INDEX uk_role_privilege ON role_privilege(role_id,privilege_id);
