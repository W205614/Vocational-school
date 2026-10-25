ALTER TABLE `user` ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0;
ALTER TABLE `user` ADD COLUMN temporary_password_expires_at DATETIME(3) NULL;
CREATE TABLE user_password_upgrade_guard (
 id INT PRIMARY KEY,
 completed TINYINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
INSERT INTO user_password_upgrade_guard(id,completed) VALUES(1,0);
