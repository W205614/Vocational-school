ALTER TABLE exam_paper_question
    ADD COLUMN lecture_section_id BIGINT NULL,
    ADD COLUMN lecture_moment INT NULL;
