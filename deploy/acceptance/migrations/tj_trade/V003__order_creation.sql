CREATE TABLE order_creation(
 order_id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,request_hash CHAR(64) NOT NULL,payload LONGTEXT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',expires_at DATETIME(3) NOT NULL,created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_creation_expiry(status,expires_at)
) ENGINE=InnoDB;
