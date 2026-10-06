CREATE TABLE cart_owner_guard(user_id BIGINT PRIMARY KEY);
ALTER TABLE cart ADD UNIQUE KEY uq_cart_user_course(user_id,course_id);
