ALTER TABLE admin_audit ADD COLUMN operation_id CHAR(36) NULL, ADD KEY ix_audit_operation(operation_id);
