CREATE TABLE exam_paper_family(course_id BIGINT NOT NULL,section_id BIGINT NOT NULL,latest_version INT NOT NULL DEFAULT 0,PRIMARY KEY(course_id,section_id)) ENGINE=InnoDB;
CREATE TABLE exam_paper(id BIGINT PRIMARY KEY,course_id BIGINT NOT NULL,section_id BIGINT NOT NULL,version INT NOT NULL,
 total_score INT NOT NULL,pass_percent INT NOT NULL DEFAULT 60,section_count INT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),UNIQUE KEY uq_paper_version(course_id,section_id,version)) ENGINE=InnoDB;
CREATE TABLE exam_paper_question(paper_id BIGINT NOT NULL,question_id BIGINT NOT NULL,position INT NOT NULL,
 name TEXT NOT NULL,type INT NOT NULL,score INT NOT NULL,options LONGTEXT,answer TEXT,analysis TEXT,
 PRIMARY KEY(paper_id,question_id),UNIQUE KEY uq_paper_position(paper_id,position)) ENGINE=InnoDB;
CREATE TABLE exam_grader(paper_id BIGINT NOT NULL,user_id BIGINT NOT NULL,PRIMARY KEY(paper_id,user_id)) ENGINE=InnoDB;
CREATE TABLE exam_attempt(id BIGINT PRIMARY KEY,paper_id BIGINT NOT NULL,user_id BIGINT NOT NULL,lesson_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'IN_PROGRESS',score INT,passed TINYINT,version BIGINT NOT NULL DEFAULT 0,
 active_paper_id BIGINT GENERATED ALWAYS AS(IF(status IN('IN_PROGRESS','WAIT_GRADING'),paper_id,NULL)) STORED,
 created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),submitted_at DATETIME(3),graded_at DATETIME(3),
 UNIQUE KEY uq_exam_active(user_id,active_paper_id),KEY idx_attempt_user(user_id,created_at),KEY idx_attempt_status(status,paper_id)) ENGINE=InnoDB;
CREATE TABLE exam_answer(attempt_id BIGINT NOT NULL,question_id BIGINT NOT NULL,response TEXT NOT NULL,
 score INT,graded_by BIGINT,feedback TEXT,version BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(attempt_id,question_id)) ENGINE=InnoDB;
