CREATE TABLE learning_entitlement_guard (
 user_id BIGINT NOT NULL,course_id BIGINT NOT NULL,
 last_active_status TINYINT NOT NULL DEFAULT 0,
 PRIMARY KEY(user_id,course_id)
) ENGINE=InnoDB;
INSERT INTO learning_entitlement_guard(user_id,course_id,last_active_status)
 SELECT l.user_id,l.course_id,CASE WHEN l.status IN(0,1,2) THEN l.status
  WHEN l.learned_sections>=c.section_num AND c.section_num>0 THEN 2
  WHEN l.learned_sections>0 THEN 1 ELSE 0 END
 FROM learning_lesson l LEFT JOIN tj_course.course c ON c.id=l.course_id;
