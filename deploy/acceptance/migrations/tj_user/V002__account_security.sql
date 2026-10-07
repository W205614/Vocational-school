ALTER TABLE `user` ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
CREATE TRIGGER user_auth_version BEFORE UPDATE ON `user` FOR EACH ROW SET NEW.auth_version=IF(NOT(OLD.password <=> NEW.password) OR NOT(OLD.status <=> NEW.status) OR NOT(OLD.type <=> NEW.type), OLD.auth_version+1, NEW.auth_version);
CREATE TRIGGER detail_auth_version AFTER UPDATE ON user_detail FOR EACH ROW UPDATE `user` SET auth_version=auth_version+IF(NOT(OLD.role_id <=> NEW.role_id) OR NOT(OLD.type <=> NEW.type),1,0) WHERE id=NEW.id;
