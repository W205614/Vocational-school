ALTER TABLE course_sales_projection ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING';
CREATE INDEX ix_sales_due ON course_sales_projection(status,next_attempt_at,lease_until);
