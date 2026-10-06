ALTER TABLE course_sales_projection ADD COLUMN lease_token VARCHAR(36), ADD COLUMN lease_until DATETIME(3), ADD COLUMN attempts INT NOT NULL DEFAULT 0, ADD COLUMN last_error VARCHAR(500), ADD COLUMN next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);
CREATE INDEX idx_sales_projection_due ON course_sales_projection(next_attempt_at,lease_until);
