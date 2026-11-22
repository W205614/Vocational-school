UPDATE course_metadata_projection
SET version = version + 1, status = 'PENDING', attempts = 0, next_attempt_at = CURRENT_TIMESTAMP(3),
    last_error = NULL, lease_token = NULL, lease_until = NULL;
