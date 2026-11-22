CREATE TABLE learning_practice_assignment_version (
    course_id BIGINT NOT NULL,
    version INT NOT NULL,
    title VARCHAR(120) NOT NULL,
    instructions TEXT NOT NULL,
    change_reason VARCHAR(1000) NOT NULL,
    effective_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    editor_id BIGINT NULL,
    PRIMARY KEY (course_id,version),
    CONSTRAINT fk_practice_version_assignment FOREIGN KEY (course_id)
        REFERENCES learning_practice_assignment(course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE learning_practice_assignment ADD COLUMN current_version INT NULL;
ALTER TABLE learning_practice_submission ADD COLUMN assignment_version INT NULL;

INSERT INTO learning_practice_assignment_version
    (course_id,version,title,instructions,change_reason,effective_at)
SELECT course_id,1,title,instructions,'首次发布',published_at
FROM learning_practice_assignment WHERE published_at IS NOT NULL;

UPDATE learning_practice_assignment SET current_version=1 WHERE published_at IS NOT NULL;
UPDATE learning_practice_submission SET assignment_version=1;

ALTER TABLE learning_practice_submission
    MODIFY COLUMN assignment_version INT NOT NULL,
    ADD CONSTRAINT fk_practice_submission_version FOREIGN KEY (course_id,assignment_version)
        REFERENCES learning_practice_assignment_version(course_id,version);
