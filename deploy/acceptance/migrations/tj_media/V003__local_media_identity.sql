ALTER TABLE media MODIFY COLUMN file_id VARCHAR(100) NOT NULL, MODIFY COLUMN request_id VARCHAR(64), ADD UNIQUE KEY uq_media_file_id(file_id);
CREATE TABLE media_registration_guard(file_id VARCHAR(100) PRIMARY KEY) ENGINE=InnoDB;
