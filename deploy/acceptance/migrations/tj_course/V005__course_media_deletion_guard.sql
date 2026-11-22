CREATE TABLE course_media_deletion_guard (
    media_id BIGINT PRIMARY KEY,
    deletion_reserved BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;

ALTER TABLE course_catalogue ADD INDEX ix_catalogue_media_reference (media_id, deleted, type);
ALTER TABLE course_catalogue_draft ADD INDEX ix_catalogue_draft_media_reference (media_id, deleted, type);
