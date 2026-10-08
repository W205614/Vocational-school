
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `acceptance_common` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `acceptance_common`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_consumer_failure` (
  `failure_id` char(36) NOT NULL,
  `event_id` varchar(128) NOT NULL,
  `queue_name` varchar(255) NOT NULL,
  `body` longblob NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'FAILED',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` varchar(1000) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`failure_id`),
  UNIQUE KEY `uk_failure_event` (`queue_name`,`event_id`),
  KEY `ix_failure_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation` (
  `operation_id` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `kind` varchar(32) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `payload` longtext NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `result` longtext,
  `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uq_operation_request` (`user_id`,`kind`,`idempotency_key`),
  KEY `idx_operation_dispatch` (`kind`,`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation_failure` (
  `operation_id` char(36) NOT NULL,
  `last_error` varchar(1000) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_outbox` (
  `event_id` varchar(64) NOT NULL,
  `business_key` varchar(190) NOT NULL,
  `exchange_name` varchar(128) NOT NULL,
  `routing_key` varchar(128) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `schema_version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `delay_ms` bigint NOT NULL DEFAULT '0',
  `status` varchar(16) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uq_outbox_business_event` (`business_key`,`event_type`),
  KEY `idx_outbox_dispatch` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `test_counter` (
  `id` int NOT NULL,
  `value` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `acceptance_learning` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `acceptance_learning`;
CREATE TABLE IF NOT EXISTS learning_entitlement (
 order_detail_id BIGINT PRIMARY KEY,order_id BIGINT NOT NULL,user_id BIGINT NOT NULL,course_id BIGINT NOT NULL,
 active TINYINT NOT NULL,expires_at DATETIME(3),created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_entitlement_user_course(user_id,course_id,active,expires_at),KEY idx_entitlement_order(order_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS learning_entitlement_guard (
 user_id BIGINT NOT NULL,course_id BIGINT NOT NULL,last_active_status TINYINT NOT NULL DEFAULT 0,
 PRIMARY KEY(user_id,course_id)
) ENGINE=InnoDB;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_lesson` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '学员id',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `status` tinyint DEFAULT '0' COMMENT '课程状态，0-未学习，1-学习中，2-已学完，3-已失效',
  `week_freq` tinyint DEFAULT NULL COMMENT '每周学习频率，例如每周学习6小节，则频率为6',
  `plan_status` tinyint NOT NULL DEFAULT '0' COMMENT '学习计划状态，0-没有计划，1-计划进行中',
  `learned_sections` int NOT NULL DEFAULT '0' COMMENT '已学习小节数量',
  `latest_section_id` bigint DEFAULT NULL COMMENT '最近一次学习的小节id',
  `latest_learn_time` datetime DEFAULT NULL COMMENT '最近一次学习的时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_user_id` (`user_id`,`course_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='学生课程表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_record` (
  `id` bigint NOT NULL COMMENT '学习记录的id',
  `lesson_id` bigint NOT NULL COMMENT '对应课表的id',
  `section_id` bigint NOT NULL COMMENT '对应小节的id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `moment` int DEFAULT '0' COMMENT '视频的当前观看时间点，单位秒',
  `finished` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否完成学习，默认false',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '第一次观看时间',
  `finish_time` datetime DEFAULT NULL COMMENT '完成学习的时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（最近一次观看时间）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uq_learning_record_section` (`lesson_id`,`section_id`),
  KEY `idx_update_time` (`update_time`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_lesson_id` (`lesson_id`,`section_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='学习记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_daily_quota` (
  `user_id` bigint NOT NULL,
  `type` int NOT NULL,
  `quota_day` date NOT NULL,
  `points` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`user_id`,`type`,`quota_day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_projection` (
  `board_month` char(6) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `points` int NOT NULL,
  `version` bigint NOT NULL,
  `processed_version` bigint NOT NULL,
  PRIMARY KEY (`board_month`,`user_id`),
  KEY `idx_points_dirty` (`processed_version`,`version`),
  KEY `idx_points_month_rank` (`board_month`,`points` DESC,`user_id` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '积分记录表id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `type` tinyint NOT NULL COMMENT '积分方式：1-课程学习，2-每日签到，3-课程问答， 4-课程笔记，5-课程评价',
  `points` tinyint NOT NULL COMMENT '积分值',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `source_event_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uq_points_event` (`type`,`source_event_id`),
  KEY `idx_user_id` (`user_id`,`type`) USING BTREE,
  KEY `idx_create_time` (`create_time`) USING BTREE,
  KEY `idx_points_user_time_type` (`user_id`,`create_time`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='学习积分记录，每个月底清零';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_consumer_failure` (
  `failure_id` char(36) NOT NULL,
  `event_id` varchar(128) NOT NULL,
  `queue_name` varchar(255) NOT NULL,
  `body` longblob NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'FAILED',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` varchar(1000) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`failure_id`),
  UNIQUE KEY `uk_failure_event` (`queue_name`,`event_id`),
  KEY `ix_failure_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation` (
  `operation_id` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `kind` varchar(32) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `payload` longtext NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `result` longtext,
  `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uq_operation_request` (`user_id`,`kind`,`idempotency_key`),
  KEY `idx_operation_dispatch` (`kind`,`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation_failure` (
  `operation_id` char(36) NOT NULL,
  `last_error` varchar(1000) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_outbox` (
  `event_id` varchar(64) NOT NULL,
  `business_key` varchar(190) NOT NULL,
  `exchange_name` varchar(128) NOT NULL,
  `routing_key` varchar(128) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `schema_version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `delay_ms` bigint NOT NULL DEFAULT '0',
  `status` varchar(16) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uq_outbox_business_event` (`business_key`,`event_type`),
  KEY `idx_outbox_dispatch` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `acceptance_exam` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `acceptance_exam`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_answer` (
  `attempt_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `response` text NOT NULL,
  `score` int DEFAULT NULL,
  `graded_by` bigint DEFAULT NULL,
  `feedback` text,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`attempt_id`,`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_attempt` (
  `id` bigint NOT NULL,
  `paper_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `lesson_id` bigint NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'IN_PROGRESS',
  `score` int DEFAULT NULL,
  `passed` tinyint DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `active_paper_id` bigint GENERATED ALWAYS AS (if((`status` in (_utf8mb4'IN_PROGRESS',_utf8mb4'WAIT_GRADING')),`paper_id`,NULL)) STORED,
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  `submitted_at` datetime(3) DEFAULT NULL,
  `graded_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_exam_active` (`user_id`,`active_paper_id`),
  KEY `idx_attempt_user` (`user_id`,`created_at`),
  KEY `idx_attempt_status` (`status`,`paper_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_grader` (
  `paper_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`paper_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_paper` (
  `id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `version` int NOT NULL,
  `total_score` int NOT NULL,
  `pass_percent` int NOT NULL DEFAULT '60',
  `section_count` int NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_paper_version` (`course_id`,`section_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_paper_family` (
  `course_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `latest_version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`course_id`,`section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_paper_question` (
  `paper_id` bigint NOT NULL,
  `question_id` bigint NOT NULL,
  `position` int NOT NULL,
  `name` text NOT NULL,
  `type` int NOT NULL,
  `score` int NOT NULL,
  `options` longtext,
  `answer` text,
  `analysis` text,
  PRIMARY KEY (`paper_id`,`question_id`),
  UNIQUE KEY `uq_paper_position` (`paper_id`,`position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_consumer_failure` (
  `failure_id` char(36) NOT NULL,
  `event_id` varchar(128) NOT NULL,
  `queue_name` varchar(255) NOT NULL,
  `body` longblob NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'FAILED',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` varchar(1000) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`failure_id`),
  UNIQUE KEY `uk_failure_event` (`queue_name`,`event_id`),
  KEY `ix_failure_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation` (
  `operation_id` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `kind` varchar(32) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `payload` longtext NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `result` longtext,
  `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uq_operation_request` (`user_id`,`kind`,`idempotency_key`),
  KEY `idx_operation_dispatch` (`kind`,`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation_failure` (
  `operation_id` char(36) NOT NULL,
  `last_error` varchar(1000) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_outbox` (
  `event_id` varchar(64) NOT NULL,
  `business_key` varchar(190) NOT NULL,
  `exchange_name` varchar(128) NOT NULL,
  `routing_key` varchar(128) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `schema_version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `delay_ms` bigint NOT NULL DEFAULT '0',
  `status` varchar(16) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uq_outbox_business_event` (`business_key`,`event_type`),
  KEY `idx_outbox_dispatch` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `acceptance_pay` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `acceptance_pay`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `biz_order_no` bigint NOT NULL COMMENT '业务订单号',
  `pay_order_no` bigint NOT NULL DEFAULT '0' COMMENT '支付单号',
  `biz_user_id` bigint NOT NULL COMMENT '支付用户id',
  `pay_channel_code` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0' COMMENT '支付渠道编码',
  `amount` int NOT NULL COMMENT '支付金额，单位位分',
  `pay_type` tinyint NOT NULL DEFAULT '4' COMMENT '支付类型，1：h5,2:小程序，3：公众号，4：扫码',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '支付状态，0：待提交，1:待支付，2：支付超时或取消，3：支付成功',
  `expand_json` varchar(1024) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '' COMMENT '拓展字段，用于传递不同渠道单独处理的字段',
  `notify_url` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '' COMMENT '业务端回调接口',
  `notify_times` int NOT NULL DEFAULT '0' COMMENT '业务端回调次数',
  `notify_status` int NOT NULL DEFAULT '0' COMMENT '回调状态，0：待回调，1：回调成功，2：回调失败',
  `result_code` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '' COMMENT '第三方返回业务码',
  `result_msg` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '' COMMENT '第三方返回提示信息',
  `pay_success_time` datetime DEFAULT NULL COMMENT '支付成功时间',
  `pay_over_time` datetime NOT NULL COMMENT '支付超时时间',
  `qr_code_url` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '支付二维码链接',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `biz_order_no` (`biz_order_no`),
  UNIQUE KEY `pay_order_no` (`pay_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='支付订单';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_payment_fact` (
  `pay_order_no` bigint NOT NULL,
  `biz_order_no` bigint NOT NULL,
  `amount` int NOT NULL,
  `success_time` datetime(3) NOT NULL,
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`pay_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_refund_conflict` (
  `refund_order_no` bigint NOT NULL,
  `observed_status` int NOT NULL,
  `recorded_status` int NOT NULL,
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`refund_order_no`,`observed_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_refund_fact` (
  `refund_order_no` bigint NOT NULL,
  `status` int NOT NULL,
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`refund_order_no`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_request_guard` (
  `kind` varchar(20) NOT NULL,
  `business_id` bigint NOT NULL,
  PRIMARY KEY (`kind`,`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refund_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `biz_order_no` bigint NOT NULL COMMENT '业务端已支付的订单id',
  `biz_refund_order_no` bigint NOT NULL COMMENT '业务端要退款的订单id，也就是子订单id',
  `pay_order_no` bigint NOT NULL COMMENT '付款时传入的支付单号',
  `refund_order_no` bigint NOT NULL COMMENT '退款单号，每次退款的唯一标示',
  `refund_amount` int NOT NULL COMMENT '本次退款金额，单位分',
  `total_amount` int NOT NULL COMMENT '总金额，单位分',
  `is_split` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否是拆单退款，默认false',
  `pay_channel_code` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT '0' COMMENT '支付渠道编码',
  `result_code` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '' COMMENT '第三方交易编码',
  `result_msg` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '' COMMENT '第三方交易信息',
  `status` int NOT NULL DEFAULT '0' COMMENT '退款状态，0：未提交，1：退款中，2：退款失败，3：退款成功',
  `refund_channel` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '退款渠道',
  `notify_failed_times` int NOT NULL DEFAULT '0' COMMENT '业务端退款通知失败次数',
  `notify_status` int NOT NULL DEFAULT '0' COMMENT '退款接口通知状态，0：待通知，1：通知成功，2：通知中，3：通知失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '退款单据创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '退款单据修改时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '单据创建人，一般手动对账产生的单据才有值',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '单据修改人，一般手动对账产生的单据才有值',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `index_biz_order_id` (`biz_refund_order_no`) USING BTREE,
  KEY `index_create_time` (`create_time`) USING BTREE,
  KEY `index_refund_order_id` (`refund_order_no`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='退款订单';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_consumer_failure` (
  `failure_id` char(36) NOT NULL,
  `event_id` varchar(128) NOT NULL,
  `queue_name` varchar(255) NOT NULL,
  `body` longblob NOT NULL,
  `content_type` varchar(128) DEFAULT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'FAILED',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` varchar(1000) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`failure_id`),
  UNIQUE KEY `uk_failure_event` (`queue_name`,`event_id`),
  KEY `ix_failure_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation` (
  `operation_id` varchar(64) NOT NULL,
  `user_id` bigint NOT NULL,
  `kind` varchar(32) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `payload` longtext NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `result` longtext,
  `error_code` varchar(64) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uq_operation_request` (`user_id`,`kind`,`idempotency_key`),
  KEY `idx_operation_dispatch` (`kind`,`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation_failure` (
  `operation_id` char(36) NOT NULL,
  `last_error` varchar(1000) NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_outbox` (
  `event_id` varchar(64) NOT NULL,
  `business_key` varchar(190) NOT NULL,
  `exchange_name` varchar(128) NOT NULL,
  `routing_key` varchar(128) NOT NULL,
  `event_type` varchar(128) NOT NULL,
  `schema_version` int NOT NULL DEFAULT '1',
  `payload` longtext NOT NULL,
  `delay_ms` bigint NOT NULL DEFAULT '0',
  `status` varchar(16) NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uq_outbox_business_event` (`business_key`,`event_type`),
  KEY `idx_outbox_dispatch` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
