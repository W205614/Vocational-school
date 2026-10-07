-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: tj_auth
-- ------------------------------------------------------
-- Server version	8.4.11

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

--
-- Current Database: `tj_auth`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_auth` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_auth`;

--
-- Table structure for table `account_role`
--

DROP TABLE IF EXISTS `account_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `account_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_id` bigint NOT NULL COMMENT '账户id',
  `role_id` bigint NOT NULL COMMENT '角色id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账户、角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `auth_cache_guard`
--

DROP TABLE IF EXISTS `auth_cache_guard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth_cache_guard` (
  `id` int NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `login_record`
--

DROP TABLE IF EXISTS `login_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `login_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `cell_phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '手机号码',
  `login_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `logout_time` datetime DEFAULT NULL COMMENT '登出时间',
  `login_date` date DEFAULT NULL COMMENT '登录日期',
  `duration` bigint DEFAULT '0' COMMENT '登录时长，单位是秒',
  `ipv4` varchar(15) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT 'ip地址',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=804 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录信息记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `menu`
--

DROP TABLE IF EXISTS `menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `menu` (
  `id` bigint NOT NULL COMMENT '主键',
  `parent_id` bigint DEFAULT '0' COMMENT '父菜单id，默认0代表没有父菜单',
  `has_children` tinyint DEFAULT '0' COMMENT '是否有子菜单，默认false',
  `label` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '菜单文本',
  `path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '菜单路径',
  `icon` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '菜单图标',
  `priority` tinyint NOT NULL DEFAULT '127' COMMENT '顺序，默认127',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建者id',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新者id',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除，默认0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表，包括菜单权限和访问路径权限';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `privilege`
--

DROP TABLE IF EXISTS `privilege`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `privilege` (
  `id` bigint NOT NULL COMMENT '主键',
  `menu_id` bigint DEFAULT NULL COMMENT '菜单id',
  `intro` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '权限说明',
  `method` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT 'API权限的请求方式',
  `uri` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT 'API权限的请求路径',
  `internal` tinyint DEFAULT '0' COMMENT '是否是内部接口权限，默认false',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建者id',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新者id',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除，默认0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表，包括菜单权限和访问路径权限';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
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

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
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

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
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

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
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

--
-- Table structure for table `role`
--

DROP TABLE IF EXISTS `role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role` (
  `id` bigint NOT NULL COMMENT '主键',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色代号，例如：admin',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '角色名称',
  `type` tinyint DEFAULT '1' COMMENT '角色类型：0-固定角色（不可选）1-自定义角色',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建者id',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新者id',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除，默认0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role_menu`
--

DROP TABLE IF EXISTS `role_menu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色id',
  `menu_id` bigint NOT NULL COMMENT '菜单id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账户、角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `role_privilege`
--

DROP TABLE IF EXISTS `role_privilege`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_privilege` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL COMMENT '角色id',
  `privilege_id` bigint NOT NULL COMMENT '权限id',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_role_privilege` (`role_id`,`privilege_id`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账户、角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_user`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_user` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_user`;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
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

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
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

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
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

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
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

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL COMMENT '主键',
  `username` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户名',
  `cell_phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '手机号',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '密码',
  `type` tinyint NOT NULL DEFAULT '0' COMMENT '用户类型：1-员工, 2-普通学员，3-老师',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '账户状态：0-禁用 1-正常',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint DEFAULT NULL COMMENT '创建者id',
  `updater` bigint DEFAULT '0' COMMENT '更新者id',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `username_idx` (`username`) USING BTREE,
  UNIQUE KEY `cell_idx` (`cell_phone`,`type`) USING BTREE,
  KEY `type_idx` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学员用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_detail`
--

DROP TABLE IF EXISTS `user_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_detail` (
  `id` bigint NOT NULL COMMENT '关联用户id',
  `type` tinyint DEFAULT '2' COMMENT '用户类型：1-员工, 2-普通学员，3-老师',
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '名字',
  `gender` tinyint NOT NULL DEFAULT '0' COMMENT '性别：0-男性，1-女性',
  `icon` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '头像地址',
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '邮箱',
  `qq` varchar(18) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'QQ号码',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `job` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '岗位',
  `province` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '省',
  `city` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '市',
  `district` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '区',
  `intro` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '个人介绍',
  `photo` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '形象照地址',
  `role_id` bigint NOT NULL COMMENT '角色id',
  `course_amount` smallint DEFAULT '0' COMMENT '购买课程数量，学生才有该字段信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint DEFAULT NULL COMMENT '创建者id',
  `updater` bigint DEFAULT '0' COMMENT '更新者id',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  PRIMARY KEY (`id`) USING BTREE,
  FULLTEXT KEY `name_idx` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师详情表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_course`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_course` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_course`;

--
-- Table structure for table `category`
--

DROP TABLE IF EXISTS `category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程分类id',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父分类id，一级分类父id为0',
  `level` int NOT NULL COMMENT '分类级别，1,2,3：代表一级分类，二级分类，三级分类',
  `priority` int NOT NULL DEFAULT '1' COMMENT '同级目录优先级，数字越小优先级越高，可以重复',
  `status` tinyint NOT NULL COMMENT '课程分类状态，1：正常，2：禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `deleted` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3656 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程分类';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course`
--

DROP TABLE IF EXISTS `course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course` (
  `id` bigint NOT NULL COMMENT '课程草稿id，对应正式草稿id',
  `name` varchar(80) NOT NULL COMMENT '课程名称',
  `course_type` tinyint NOT NULL DEFAULT '2' COMMENT '课程类型，1：直播课，2：录播课',
  `cover_url` varchar(255) NOT NULL COMMENT '封面链接',
  `first_cate_id` bigint NOT NULL COMMENT '一级课程分类id',
  `second_cate_id` bigint NOT NULL DEFAULT '0' COMMENT '二级课程分类id',
  `third_cate_id` bigint NOT NULL DEFAULT '0' COMMENT '三级课程分类id',
  `free` tinyint NOT NULL DEFAULT '0' COMMENT '售卖方式0付费，1：免费',
  `price` int NOT NULL COMMENT '课程价格，单位为分',
  `template_type` tinyint NOT NULL DEFAULT '1' COMMENT '模板类型，1：固定模板，2：自定义模板',
  `template_url` varchar(255) NOT NULL DEFAULT '' COMMENT '自定义模板的连接',
  `status` tinyint NOT NULL COMMENT '课程状态，1：待上架，2：已上架，3：下架，4：已完结',
  `purchase_start_time` datetime DEFAULT NULL COMMENT '课程购买有效期开始时间',
  `purchase_end_time` datetime NOT NULL COMMENT '课程购买有效期结束时间',
  `step` tinyint NOT NULL COMMENT '信息填写进度',
  `score` int DEFAULT '0' COMMENT '课程评价得分，45代表4.5星',
  `media_duration` int(10) unsigned zerofill DEFAULT NULL COMMENT '课程总时长',
  `valid_duration` int NOT NULL COMMENT '课程有效期，单位月',
  `section_num` int DEFAULT NULL COMMENT '课程总节数，包括练习',
  `dep_id` bigint NOT NULL COMMENT '部门id',
  `publish_times` int DEFAULT '1' COMMENT '发布次数',
  `publish_time` datetime DEFAULT NULL COMMENT '最近一次发布时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='草稿课程';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_cata_subject_draft`
--

DROP TABLE IF EXISTS `course_cata_subject_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_cata_subject_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '小节题目关系id',
  `course_id` bigint DEFAULT NULL,
  `cata_id` bigint NOT NULL COMMENT '小节id',
  `subject_id` bigint NOT NULL COMMENT '题目id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=154 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程-题目关系表草稿';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_catalogue`
--

DROP TABLE IF EXISTS `course_catalogue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_catalogue` (
  `id` bigint NOT NULL COMMENT '课程目录id',
  `name` varchar(255) NOT NULL COMMENT '目录名称',
  `trailer` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持试看',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `type` tinyint NOT NULL COMMENT '目录类型1：章，2：节，3：测试',
  `parent_catalogue_id` bigint NOT NULL DEFAULT '0' COMMENT '所属章id，只有小节和测试有该值，章没有，章默认为0',
  `media_id` bigint NOT NULL DEFAULT '0' COMMENT '媒资id',
  `video_id` bigint DEFAULT NULL COMMENT '视频id',
  `video_name` varchar(255) NOT NULL DEFAULT '' COMMENT '视频名称',
  `living_start_time` datetime DEFAULT NULL COMMENT '直播开始时间',
  `living_end_time` datetime DEFAULT NULL COMMENT '直播结束时间',
  `play_back` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持回放',
  `media_duration` int NOT NULL DEFAULT '0' COMMENT '视频时长，以秒为单位',
  `c_index` int NOT NULL DEFAULT '0' COMMENT '用于章节排序',
  `dep_id` bigint NOT NULL COMMENT '部门id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目录草稿';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_catalogue_draft`
--

DROP TABLE IF EXISTS `course_catalogue_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_catalogue_draft` (
  `id` bigint NOT NULL COMMENT '课程目录id',
  `name` varchar(255) NOT NULL COMMENT '目录名称',
  `trailer` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持试看',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `type` tinyint NOT NULL COMMENT '目录类型1：章节，2：小节，3：测试',
  `parent_catalogue_id` bigint NOT NULL DEFAULT '0' COMMENT '所属章节id，只有小节和测试有该值，章节没有，章节默认为0',
  `media_id` bigint NOT NULL DEFAULT '0' COMMENT '媒资id',
  `video_id` bigint DEFAULT NULL COMMENT '视频id',
  `video_name` varchar(255) NOT NULL DEFAULT '' COMMENT '视频名称',
  `living_start_time` datetime DEFAULT NULL COMMENT '直播开始时间',
  `living_end_time` datetime DEFAULT NULL COMMENT '直播结束时间',
  `play_back` tinyint NOT NULL DEFAULT '0' COMMENT '是否支持回放',
  `c_index` int NOT NULL DEFAULT '0' COMMENT '用于章节排序',
  `media_duration` int NOT NULL DEFAULT '0' COMMENT '以s为单位',
  `can_update` tinyint NOT NULL DEFAULT '1' COMMENT '是否可以更新0：不可以更新，1：可以更新',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='目录草稿';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_content`
--

DROP TABLE IF EXISTS `course_content`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_content` (
  `id` bigint NOT NULL COMMENT '课程内容id',
  `course_introduce` varchar(512) NOT NULL COMMENT '课程介绍',
  `use_people` varchar(512) NOT NULL COMMENT '适用人群',
  `course_detail` varchar(1024) NOT NULL COMMENT '课程详情',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程内容，主要是一些大文本';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_content_draft`
--

DROP TABLE IF EXISTS `course_content_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_content_draft` (
  `id` bigint NOT NULL COMMENT '课程内容id',
  `course_introduce` varchar(512) NOT NULL COMMENT '课程介绍',
  `use_people` varchar(512) NOT NULL COMMENT '适用人群',
  `course_detail` varchar(1024) NOT NULL COMMENT '课程详情',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程内容，主要是一些大文本';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_draft`
--

DROP TABLE IF EXISTS `course_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_draft` (
  `id` bigint NOT NULL COMMENT '课程草稿id，对应正式草稿id',
  `name` varchar(80) NOT NULL COMMENT '课程名称',
  `course_type` tinyint NOT NULL DEFAULT '2' COMMENT '课程类型，1：直播课，2：录播课',
  `cover_url` varchar(255) NOT NULL COMMENT '封面链接',
  `first_cate_id` bigint NOT NULL COMMENT '一级课程分类id',
  `second_cate_id` bigint NOT NULL DEFAULT '0' COMMENT '二级课程分类id',
  `third_cate_id` bigint NOT NULL DEFAULT '0' COMMENT '三级课程分类id',
  `free` tinyint NOT NULL DEFAULT '0' COMMENT '售卖方式0付费，1：免费',
  `price` int NOT NULL COMMENT '课程价格，单位为分',
  `template_type` tinyint NOT NULL DEFAULT '1' COMMENT '模板类型，1：固定模板，2：自定义模板',
  `template_url` varchar(255) NOT NULL DEFAULT '' COMMENT '自定义模板的连接',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '课程状态，0：待上架，1：已上架，2：下架，3：已完结',
  `purchase_start_time` datetime DEFAULT NULL COMMENT '课程购买有效期开始时间',
  `purchase_end_time` datetime NOT NULL COMMENT '课程购买有效期结束时间',
  `step` tinyint NOT NULL COMMENT '信息填写进度1：基本信息已经保存，2：课程目录已经保存，3：课程视频已保存，4：课程题目已保存，5：课程老师已经保存',
  `score` int DEFAULT '0' COMMENT '课程评价得分，45代表4.5星',
  `media_duration` int NOT NULL DEFAULT '0' COMMENT '视频总时长',
  `valid_duration` int NOT NULL DEFAULT '0' COMMENT '课程有效期，单位月',
  `section_num` int NOT NULL DEFAULT '0' COMMENT '课程总节数',
  `can_update` tinyint NOT NULL DEFAULT '1' COMMENT '是否可以更新',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  `publish_time` datetime DEFAULT NULL COMMENT '最近一次发布时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `c_version` int DEFAULT '1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='草稿课程';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_state_guard`
--

DROP TABLE IF EXISTS `course_state_guard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_state_guard` (
  `course_id` bigint NOT NULL,
  PRIMARY KEY (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_subject`
--

DROP TABLE IF EXISTS `course_subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_subject` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程题目关系id',
  `course_id` bigint NOT NULL,
  `subject_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程题目关系列表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_teacher`
--

DROP TABLE IF EXISTS `course_teacher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_teacher` (
  `id` bigint NOT NULL COMMENT '课程老师关系id',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `teacher_id` bigint NOT NULL COMMENT '老师id',
  `is_show` tinyint NOT NULL DEFAULT '0' COMMENT '用户端是否展示',
  `c_index` int NOT NULL COMMENT '序号',
  `dep_id` bigint NOT NULL COMMENT '部门id',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程老师关系表草稿';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_teacher_draft`
--

DROP TABLE IF EXISTS `course_teacher_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_teacher_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程老师关系id',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `teacher_id` bigint NOT NULL COMMENT '老师id',
  `is_show` tinyint NOT NULL DEFAULT '0' COMMENT '用户端是否展示',
  `c_index` int NOT NULL COMMENT '序号',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=810000017913472141 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程老师关系表草稿';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
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

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
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

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
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

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
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

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subject`
--

DROP TABLE IF EXISTS `subject`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subject` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题目id',
  `name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '题干',
  `subject_type` tinyint NOT NULL COMMENT '题目类型，1：单选题，2：多选题，3：不定向选择题，4：判断题，5：主观题',
  `difficulty` tinyint NOT NULL COMMENT '难易度，1：简单，2：中等，3：困难',
  `option1` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '选择题答案1，',
  `option2` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '选择题答案2',
  `option3` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '选择题答案3',
  `option4` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '选择题答案4',
  `option5` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '选择题答案5',
  `option6` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '选择题答案6',
  `option7` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '选择题答案7',
  `option8` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '选择题答案8',
  `option9` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '选择题答案9',
  `option10` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '选择题答案10',
  `answer` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '选择题正确答案1到10，如果有多个答案，中间使用逗号隔开，如果是判断题，1：代表正确，其他代表错误',
  `analysis` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '答案解析',
  `use_times` int NOT NULL DEFAULT '0' COMMENT '引用次数',
  `answer_times` int NOT NULL DEFAULT '0' COMMENT '回答次数',
  `score` int NOT NULL COMMENT '分值',
  `dep_id` bigint DEFAULT NULL COMMENT '部门id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `subject_category`
--

DROP TABLE IF EXISTS `subject_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subject_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `subject_id` bigint NOT NULL COMMENT '题目id',
  `first_cate_id` bigint NOT NULL COMMENT '一级课程分类id',
  `second_cate_id` bigint DEFAULT NULL COMMENT '二级课程分类id',
  `third_cate_id` bigint DEFAULT NULL COMMENT '三级课程分类id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5334 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='课程分类关系表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `undo_log`
--

DROP TABLE IF EXISTS `undo_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='AT transaction mode undo table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_learning`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_learning` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_learning`;

--
-- Table structure for table `course_favorite`
--

DROP TABLE IF EXISTS `course_favorite`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_favorite` (
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`user_id`,`course_id`),
  KEY `idx_favorite_time` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_note`
--

DROP TABLE IF EXISTS `course_note`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_note` (
  `id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `section_id` bigint DEFAULT NULL,
  `moment` int DEFAULT NULL,
  `content` text COLLATE utf8mb4_general_ci NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_note_user_course` (`user_id`,`deleted`,`course_id`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `interaction_question`
--

DROP TABLE IF EXISTS `interaction_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `interaction_question` (
  `id` bigint NOT NULL COMMENT '主键，互动问题的id',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '互动问题的标题',
  `description` varchar(2048) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '问题描述信息',
  `course_id` bigint NOT NULL COMMENT '所属课程id',
  `chapter_id` bigint NOT NULL COMMENT '所属课程章id',
  `section_id` bigint NOT NULL COMMENT '所属课程节id',
  `user_id` bigint NOT NULL COMMENT '提问学员id',
  `latest_answer_id` bigint DEFAULT NULL COMMENT '最新的一个回答的id',
  `answer_times` int unsigned NOT NULL DEFAULT '0' COMMENT '问题下的回答数量',
  `anonymity` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否匿名，默认false',
  `hidden` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否被隐藏，默认false',
  `status` tinyint DEFAULT '0' COMMENT '管理端问题状态：0-未查看，1-已查看',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提问时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_course_id` (`course_id`) USING BTREE,
  KEY `section_id` (`section_id`),
  KEY `user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='互动提问的问题表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `interaction_reply`
--

DROP TABLE IF EXISTS `interaction_reply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `interaction_reply` (
  `id` bigint NOT NULL COMMENT '互动问题的回答id',
  `question_id` bigint NOT NULL COMMENT '互动问题问题id',
  `answer_id` bigint DEFAULT '0' COMMENT '回复的上级回答id',
  `user_id` bigint NOT NULL COMMENT '回答者id',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '回答内容',
  `target_user_id` bigint DEFAULT '0' COMMENT '回复的目标用户id',
  `target_reply_id` bigint DEFAULT '0' COMMENT '回复的目标回复id',
  `reply_times` int NOT NULL DEFAULT '0' COMMENT '评论数量',
  `liked_times` int NOT NULL DEFAULT '0' COMMENT '点赞数量',
  `hidden` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否被隐藏，默认false',
  `anonymity` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否匿名，默认false',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `liked_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_question_id` (`question_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='互动问题的回答或评论';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `learning_entitlement`
--

DROP TABLE IF EXISTS `learning_entitlement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_entitlement` (
  `order_detail_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `active` tinyint NOT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`order_detail_id`),
  KEY `idx_entitlement_user_course` (`user_id`,`course_id`,`active`,`expires_at`),
  KEY `idx_entitlement_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `learning_lesson`
--

DROP TABLE IF EXISTS `learning_lesson`;
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

--
-- Table structure for table `learning_progress_pending`
--

DROP TABLE IF EXISTS `learning_progress_pending`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_progress_pending` (
  `record_id` bigint NOT NULL,
  `lesson_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `moment` int NOT NULL,
  `version` bigint NOT NULL,
  `processed_version` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`record_id`),
  KEY `idx_progress_dirty` (`updated_at`,`processed_version`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `learning_record`
--

DROP TABLE IF EXISTS `learning_record`;
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

--
-- Table structure for table `points_board`
--

DROP TABLE IF EXISTS `points_board`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_board` (
  `id` bigint NOT NULL COMMENT '榜单id',
  `user_id` bigint NOT NULL COMMENT '学生id',
  `points` int NOT NULL COMMENT '积分值',
  `rank` tinyint NOT NULL COMMENT '名次，只记录赛季前100',
  `season` smallint NOT NULL COMMENT '赛季，例如 1,就是第一赛季，2-就是第二赛季',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `idx_season_user` (`season`,`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='学霸天梯榜';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `points_board_season`
--

DROP TABLE IF EXISTS `points_board_season`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_board_season` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '自增长id，season标示',
  `name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '赛季名称，例如：第1赛季',
  `begin_time` date NOT NULL COMMENT '赛季开始时间',
  `end_time` date NOT NULL COMMENT '赛季结束时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `points_daily_quota`
--

DROP TABLE IF EXISTS `points_daily_quota`;
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

--
-- Table structure for table `points_projection`
--

DROP TABLE IF EXISTS `points_projection`;
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

--
-- Table structure for table `points_record`
--

DROP TABLE IF EXISTS `points_record`;
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
) ENGINE=InnoDB AUTO_INCREMENT=880000017913372534 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='学习积分记录，每个月底清零';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_consumer_failure` (
  `failure_id` char(36) COLLATE utf8mb4_general_ci NOT NULL,
  `event_id` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `queue_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `body` longblob NOT NULL,
  `content_type` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'FAILED',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`failure_id`),
  UNIQUE KEY `uk_failure_event` (`queue_name`,`event_id`),
  KEY `ix_failure_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `event_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation` (
  `operation_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `kind` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `request_hash` char(64) COLLATE utf8mb4_general_ci NOT NULL,
  `payload` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING',
  `result` longtext COLLATE utf8mb4_general_ci,
  `error_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uq_operation_request` (`user_id`,`kind`,`idempotency_key`),
  KEY `idx_operation_dispatch` (`kind`,`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation_failure` (
  `operation_id` char(36) COLLATE utf8mb4_general_ci NOT NULL,
  `last_error` varchar(1000) COLLATE utf8mb4_general_ci NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_outbox` (
  `event_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `business_key` varchar(190) COLLATE utf8mb4_general_ci NOT NULL,
  `exchange_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `routing_key` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `event_type` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `schema_version` int NOT NULL DEFAULT '1',
  `payload` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `delay_ms` bigint NOT NULL DEFAULT '0',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL,
  `last_error` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uq_outbox_business_event` (`business_key`,`event_type`),
  KEY `idx_outbox_dispatch` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `checksum` char(64) COLLATE utf8mb4_general_ci NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sign_record`
--

DROP TABLE IF EXISTS `sign_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sign_record` (
  `user_id` bigint NOT NULL,
  `sign_day` date NOT NULL,
  `sign_days` int NOT NULL,
  `reward_points` int NOT NULL,
  PRIMARY KEY (`user_id`,`sign_day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_trade`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_trade` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_trade`;

--
-- Table structure for table `cart`
--

DROP TABLE IF EXISTS `cart`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cart` (
  `id` bigint NOT NULL COMMENT '购物车条目id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `cover_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '课程封面路径',
  `course_name` varchar(255) NOT NULL COMMENT '课程名称',
  `price` int NOT NULL COMMENT '单价',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_cart_user_course` (`user_id`,`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车条目信息，也就是购物车中的课程';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cart_owner_guard`
--

DROP TABLE IF EXISTS `cart_owner_guard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cart_owner_guard` (
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `free_enrollment`
--

DROP TABLE IF EXISTS `free_enrollment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `free_enrollment` (
  `user_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `created_at` timestamp(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`user_id`,`course_id`),
  UNIQUE KEY `uq_free_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order`
--

DROP TABLE IF EXISTS `order`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order` (
  `id` bigint NOT NULL COMMENT '订单id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `pay_order_no` bigint DEFAULT NULL COMMENT '交易流水支付单号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '订单状态，1：待支付，2：已支付，3：已关闭，4：已完成，5：已报名，6：已申请退款',
  `message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '状态备注',
  `total_amount` int NOT NULL COMMENT '订单总金额，单位分',
  `real_amount` int NOT NULL COMMENT '实付金额，单位分',
  `discount_amount` int NOT NULL DEFAULT '0' COMMENT '优惠金额，单位分',
  `pay_channel` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT '' COMMENT '支付渠道',
  `coupon_ids` json DEFAULT NULL COMMENT '优惠券id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建订单时间',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `close_time` datetime DEFAULT NULL COMMENT '订单关闭时间',
  `finish_time` datetime DEFAULT NULL COMMENT '订单完成时间，支付后30天',
  `refund_time` datetime DEFAULT NULL COMMENT '申请退款时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_order_user_status_time` (`user_id`,`status`,`create_time`),
  KEY `ix_order_user_created` (`user_id`,`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_creation`
--

DROP TABLE IF EXISTS `order_creation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_creation` (
  `order_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `request_hash` char(64) NOT NULL,
  `payload` longtext NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`order_id`),
  KEY `idx_creation_expiry` (`status`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_detail`
--

DROP TABLE IF EXISTS `order_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_detail` (
  `id` bigint NOT NULL COMMENT '订单明细id',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `course_id` bigint NOT NULL COMMENT '课程id',
  `price` int NOT NULL COMMENT '课程价格',
  `name` varchar(128) NOT NULL COMMENT '课程名称',
  `cover_url` varchar(255) NOT NULL COMMENT '封面地址',
  `valid_duration` int DEFAULT NULL COMMENT '课程学习有效期，单位月。空则代表永久有效',
  `course_expire_time` datetime DEFAULT NULL COMMENT '课程学习的过期时间，支付成功开始计时',
  `discount_amount` int NOT NULL DEFAULT '0' COMMENT '折扣金额',
  `real_pay_amount` int NOT NULL COMMENT '实付金额',
  `status` tinyint NOT NULL COMMENT '订单详情状态，1：待支付，2：已支付，3：已关闭，4：已完成，5：已报名',
  `refund_status` tinyint DEFAULT NULL COMMENT '1：待审批，2：取消退款，3：同意退款，4：拒绝退款，5：退款成功，6：退款失败''',
  `pay_channel` varchar(50) NOT NULL DEFAULT '' COMMENT '支付渠道名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_user_course` (`user_id`,`course_id`),
  KEY `idx_course_expire_time` (`course_expire_time`),
  KEY `idx_pay_channel` (`pay_channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment_conflict`
--

DROP TABLE IF EXISTS `payment_conflict`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_conflict` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `pay_order_no` bigint NOT NULL,
  `reason` varchar(64) NOT NULL,
  `status` varchar(16) NOT NULL,
  `resolution` text,
  `version` bigint NOT NULL DEFAULT '0',
  `resolved_by` bigint DEFAULT NULL,
  `resolved_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_conflict_payment` (`pay_order_no`),
  KEY `idx_conflict_status` (`status`,`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment_fact`
--

DROP TABLE IF EXISTS `payment_fact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_fact` (
  `pay_order_no` bigint NOT NULL,
  `order_id` bigint NOT NULL,
  `pay_channel` varchar(64) DEFAULT NULL,
  `paid_at` datetime(3) DEFAULT NULL,
  `recorded_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`pay_order_no`),
  KEY `idx_payment_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment_reconcile`
--

DROP TABLE IF EXISTS `payment_reconcile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_reconcile` (
  `order_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` char(36) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) NOT NULL,
  `last_error` varchar(1000) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`order_id`),
  KEY `ix_payment_reconcile` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `refund_apply`
--

DROP TABLE IF EXISTS `refund_apply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refund_apply` (
  `id` bigint NOT NULL COMMENT '退款id',
  `order_detail_id` bigint NOT NULL COMMENT '订单明细id',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `pay_order_no` bigint DEFAULT NULL COMMENT '流水支付单号',
  `refund_order_no` bigint DEFAULT NULL COMMENT '流水退款单号',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '订单所属用户id',
  `refund_amount` bigint NOT NULL COMMENT '退款金额',
  `status` int NOT NULL DEFAULT '1' COMMENT '退款状态，1：待审批，2：取消退款，3：同意退款，4：拒绝退款，5：退款成功，6：退款失败',
  `refund_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '申请退款原因',
  `message` varchar(255) NOT NULL COMMENT '退款状态描述',
  `approver` bigint DEFAULT NULL COMMENT '审批人id',
  `approve_opinion` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '审批意见',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '审批备注',
  `failed_reason` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '退款失败原因',
  `question_desc` varchar(255) DEFAULT NULL COMMENT '退款问题说明',
  `refund_channel` varchar(50) DEFAULT NULL COMMENT '退款渠道',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建退款申请时间',
  `approve_time` datetime DEFAULT NULL COMMENT '审批时间',
  `finish_time` datetime DEFAULT NULL COMMENT '退款完成时间（成功或失败）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `active_detail_id` bigint GENERATED ALWAYS AS (if((`status` in (1,3)),`order_detail_id`,NULL)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_active_refund` (`active_detail_id`),
  KEY `ix_refund_user_time` (`user_id`,`create_time`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款申请';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `refund_delivery_task`
--

DROP TABLE IF EXISTS `refund_delivery_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refund_delivery_task` (
  `refund_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` char(36) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_error` varchar(1000) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`refund_id`),
  KEY `ix_refund_delivery` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
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

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
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

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
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

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
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

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `undo_log`
--

DROP TABLE IF EXISTS `undo_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='AT transaction mode undo table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_promotion`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_promotion` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_promotion`;

--
-- Table structure for table `coupon`
--

DROP TABLE IF EXISTS `coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '优惠券id',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '0' COMMENT '优惠券名称，可以和活动名称保持一致',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '优惠券类型，1：普通券。目前就一种，保留字段',
  `discount_type` tinyint NOT NULL COMMENT '折扣类型，1：满减，2：每满减，3：折扣，4：无门槛',
  `specific` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否限定作用范围，false：不限定，true：限定。默认false',
  `discount_value` int NOT NULL DEFAULT '1' COMMENT '折扣值，如果是满减则存满减金额，如果是折扣，则存折扣率，8折就是存80',
  `threshold_amount` int NOT NULL DEFAULT '0' COMMENT '使用门槛，0：表示无门槛，其他值：最低消费金额',
  `max_discount_amount` int NOT NULL DEFAULT '0' COMMENT '最高优惠金额，满减最大，0：表示没有限制，不为0，则表示该券有金额的限制',
  `obtain_way` tinyint NOT NULL DEFAULT '0' COMMENT '获取方式：1：手动领取，2：兑换码',
  `issue_begin_time` datetime DEFAULT NULL COMMENT '开始发放时间',
  `issue_end_time` datetime DEFAULT NULL COMMENT '结束发放时间',
  `term_days` int NOT NULL DEFAULT '0' COMMENT '优惠券有效期天数，0：表示有效期是指定有效期的',
  `term_begin_time` datetime DEFAULT NULL COMMENT '优惠券有效期开始时间',
  `term_end_time` datetime DEFAULT NULL COMMENT '优惠券有效期结束时间',
  `status` tinyint DEFAULT '1' COMMENT '优惠券配置状态，1：待发放，2：未开始   3：进行中，4：已结束，5：暂停',
  `total_num` int NOT NULL DEFAULT '0' COMMENT '总数量，不超过5000',
  `issue_num` int NOT NULL DEFAULT '0' COMMENT '已发行数量，用于判断是否超发',
  `used_num` int NOT NULL DEFAULT '0' COMMENT '已使用数量',
  `user_limit` int NOT NULL DEFAULT '1' COMMENT '每个人限领的数量，默认1',
  `ext_param` json DEFAULT NULL COMMENT '拓展参数字段，保留字段',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=2021870069042122754 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券的规则信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `coupon_reservation`
--

DROP TABLE IF EXISTS `coupon_reservation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupon_reservation` (
  `order_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `coupon_ids` text COLLATE utf8mb4_general_ci NOT NULL,
  `request_hash` char(64) COLLATE utf8mb4_general_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`order_id`),
  KEY `idx_reservation_expiry` (`status`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `coupon_scope`
--

DROP TABLE IF EXISTS `coupon_scope`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coupon_scope` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` tinyint NOT NULL COMMENT '范围限定类型：1-分类，2-课程，等等',
  `coupon_id` bigint NOT NULL COMMENT '优惠券id',
  `biz_id` bigint NOT NULL COMMENT '优惠券作用范围的业务id，例如分类id、课程id',
  PRIMARY KEY (`id`),
  KEY `idx_coupon` (`coupon_id`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='优惠券作用范围信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exchange_code`
--

DROP TABLE IF EXISTS `exchange_code`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exchange_code` (
  `id` int NOT NULL COMMENT '兑换码id',
  `code` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '兑换码',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '兑换码状态， 1：待兑换，2：已兑换，3：兑换活动已结束',
  `user_id` bigint NOT NULL DEFAULT '0' COMMENT '兑换人',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '兑换类型，1：优惠券，以后再添加其它类型',
  `exchange_target_id` bigint NOT NULL DEFAULT '0' COMMENT '兑换码目标id，例如兑换优惠券，该id则是优惠券的配置id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `expired_time` datetime NOT NULL COMMENT '兑换码过期时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `index_status` (`status`) USING BTREE,
  KEY `index_config_id` (`exchange_target_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='兑换码';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `promotion`
--

DROP TABLE IF EXISTS `promotion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `promotion` (
  `id` bigint NOT NULL COMMENT '促销活动id',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '活动名称',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '促销活动类型：1-优惠券，2-分销',
  `hot` tinyint NOT NULL DEFAULT '0' COMMENT '是否是热门活动：true或false，默认false',
  `begin_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='促销活动，形式多种多样，例如：优惠券';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_consumer_failure` (
  `failure_id` char(36) COLLATE utf8mb4_general_ci NOT NULL,
  `event_id` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `queue_name` varchar(255) COLLATE utf8mb4_general_ci NOT NULL,
  `body` longblob NOT NULL,
  `content_type` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'FAILED',
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`failure_id`),
  UNIQUE KEY `uk_failure_event` (`queue_name`,`event_id`),
  KEY `ix_failure_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `event_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation` (
  `operation_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `kind` varchar(32) COLLATE utf8mb4_general_ci NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `request_hash` char(64) COLLATE utf8mb4_general_ci NOT NULL,
  `payload` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING',
  `result` longtext COLLATE utf8mb4_general_ci,
  `error_code` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uq_operation_request` (`user_id`,`kind`,`idempotency_key`),
  KEY `idx_operation_dispatch` (`kind`,`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_operation_failure` (
  `operation_id` char(36) COLLATE utf8mb4_general_ci NOT NULL,
  `last_error` varchar(1000) COLLATE utf8mb4_general_ci NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_outbox` (
  `event_id` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `business_key` varchar(190) COLLATE utf8mb4_general_ci NOT NULL,
  `exchange_name` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `routing_key` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `event_type` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `schema_version` int NOT NULL DEFAULT '1',
  `payload` longtext COLLATE utf8mb4_general_ci NOT NULL,
  `delay_ms` bigint NOT NULL DEFAULT '0',
  `status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL,
  `last_error` varchar(1000) COLLATE utf8mb4_general_ci DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uq_outbox_business_event` (`business_key`,`event_type`),
  KEY `idx_outbox_dispatch` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) COLLATE utf8mb4_general_ci NOT NULL,
  `checksum` char(64) COLLATE utf8mb4_general_ci NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_coupon`
--

DROP TABLE IF EXISTS `user_coupon`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户券id',
  `user_id` bigint NOT NULL COMMENT '优惠券的拥有者',
  `coupon_id` bigint NOT NULL COMMENT '优惠券模板id',
  `term_begin_time` datetime DEFAULT NULL COMMENT '优惠券有效期开始时间',
  `term_end_time` datetime NOT NULL COMMENT '优惠券有效期结束时间',
  `used_time` datetime DEFAULT NULL COMMENT '优惠券使用时间（核销时间）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '优惠券状态，1：未使用，2：已使用，3：已失效',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `claim_operation_id` varchar(64) DEFAULT NULL,
  `reserved_order_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uq_coupon_claim_operation` (`claim_operation_id`),
  KEY `idx_coupon` (`coupon_id`),
  KEY `idx_user_coupon` (`user_id`,`coupon_id`) USING BTREE,
  KEY `idx_coupon_user_status_time` (`user_id`,`status`,`term_end_time`),
  KEY `idx_coupon_reservation` (`reserved_order_id`),
  KEY `idx_user_coupon_claim_limit` (`user_id`,`coupon_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2022685922535510019 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户领取优惠券的记录，是真正使用的优惠券信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_exam`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_exam` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_exam`;

--
-- Table structure for table `exam_answer`
--

DROP TABLE IF EXISTS `exam_answer`;
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

--
-- Table structure for table `exam_attempt`
--

DROP TABLE IF EXISTS `exam_attempt`;
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

--
-- Table structure for table `exam_draft`
--

DROP TABLE IF EXISTS `exam_draft`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_draft` (
  `attempt_id` bigint NOT NULL,
  `answers` longtext NOT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exam_grader`
--

DROP TABLE IF EXISTS `exam_grader`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_grader` (
  `paper_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`paper_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exam_paper`
--

DROP TABLE IF EXISTS `exam_paper`;
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

--
-- Table structure for table `exam_paper_family`
--

DROP TABLE IF EXISTS `exam_paper_family`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_paper_family` (
  `course_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `latest_version` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`course_id`,`section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exam_paper_question`
--

DROP TABLE IF EXISTS `exam_paper_question`;
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

--
-- Table structure for table `question`
--

DROP TABLE IF EXISTS `question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question` (
  `id` bigint NOT NULL COMMENT '题目id',
  `name` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '题干',
  `type` tinyint NOT NULL COMMENT '题目类型，1：单选题，2：多选题，3：不定向选择题，4：判断题，5：主观题',
  `cate_id1` bigint NOT NULL COMMENT '1级课程分类id',
  `cate_id2` bigint NOT NULL COMMENT '2级课程分类id',
  `cate_id3` bigint NOT NULL COMMENT '3级课程分类id',
  `difficulty` tinyint NOT NULL COMMENT '难易度，1：简单，2：中等，3：困难',
  `answer_times` int NOT NULL DEFAULT '0' COMMENT '回答次数',
  `correct_times` int NOT NULL DEFAULT '0' COMMENT '回答正确次数',
  `score` int NOT NULL COMMENT '分值',
  `dep_id` bigint DEFAULT NULL COMMENT '部门id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '1' COMMENT '创建人',
  `updater` bigint NOT NULL DEFAULT '1' COMMENT '更新人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question_biz`
--

DROP TABLE IF EXISTS `question_biz`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_biz` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `biz_id` bigint DEFAULT NULL COMMENT '业务id，要关联问题的某业务id，例如小节id',
  `question_id` bigint DEFAULT NULL COMMENT '问题id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `biz_id` (`biz_id`,`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=173 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问题和业务关联表，例如把小节id和问题id关联，一个小节下可以有多个问题';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question_detail`
--

DROP TABLE IF EXISTS `question_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_detail` (
  `id` bigint NOT NULL COMMENT '题目id',
  `options` json DEFAULT NULL COMMENT '选择题选项，json数组格式',
  `answer` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '选择题正确答案1到10，如果有多个答案，中间使用逗号隔开，如果是判断题，1：代表正确，其他代表错误',
  `analysis` varchar(1024) NOT NULL COMMENT '答案解析',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='题目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
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

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
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

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
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

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
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

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `undo_log`
--

DROP TABLE IF EXISTS `undo_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `undo_log` (
  `branch_id` bigint NOT NULL COMMENT 'branch transaction id',
  `xid` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'global transaction id',
  `context` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT 'undo_log context,such as serialization',
  `rollback_info` longblob NOT NULL COMMENT 'rollback info',
  `log_status` int NOT NULL COMMENT '0:normal status,1:defense status',
  `log_created` datetime(6) NOT NULL COMMENT 'create datetime',
  `log_modified` datetime(6) NOT NULL COMMENT 'modify datetime',
  UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 ROW_FORMAT=COMPACT COMMENT='AT transaction mode undo table';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_remark`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_remark` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_remark`;

--
-- Table structure for table `liked_counter`
--

DROP TABLE IF EXISTS `liked_counter`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `liked_counter` (
  `biz_type` varchar(32) NOT NULL,
  `biz_id` bigint NOT NULL,
  `liked_times` int NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `liked_record`
--

DROP TABLE IF EXISTS `liked_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `liked_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `biz_id` bigint NOT NULL COMMENT '点赞的业务id',
  `biz_type` varchar(16) NOT NULL COMMENT '点赞的业务类型',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_biz_user` (`biz_id`,`user_id`),
  UNIQUE KEY `uq_like_relation` (`user_id`,`biz_type`,`biz_id`)
) ENGINE=InnoDB AUTO_INCREMENT=133 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点赞记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
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

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
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

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
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

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
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

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_pay`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_pay` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_pay`;

--
-- Table structure for table `pay_channel`
--

DROP TABLE IF EXISTS `pay_channel`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pay_channel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '支付渠道id',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '支付渠道名称',
  `channel_code` varchar(30) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '支付渠道编码，用于获取支付实现',
  `channel_priority` int NOT NULL COMMENT '渠道优先级，数字越小优先级越高',
  `channel_icon` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '渠道图标',
  `status` int NOT NULL DEFAULT '1' COMMENT '支付渠道状态，1：使用中，2：停用',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb3 COMMENT='支付渠道';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `pay_order`
--

DROP TABLE IF EXISTS `pay_order`;
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
) ENGINE=InnoDB AUTO_INCREMENT=2107689331074502659 DEFAULT CHARSET=utf8mb3 COMMENT='支付订单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `provider_payment_fact`
--

DROP TABLE IF EXISTS `provider_payment_fact`;
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

--
-- Table structure for table `provider_refund_conflict`
--

DROP TABLE IF EXISTS `provider_refund_conflict`;
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

--
-- Table structure for table `provider_refund_fact`
--

DROP TABLE IF EXISTS `provider_refund_fact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_refund_fact` (
  `refund_order_no` bigint NOT NULL,
  `status` int NOT NULL,
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`refund_order_no`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `provider_request_guard`
--

DROP TABLE IF EXISTS `provider_request_guard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_request_guard` (
  `kind` varchar(20) NOT NULL,
  `business_id` bigint NOT NULL,
  PRIMARY KEY (`kind`,`business_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `refund_order`
--

DROP TABLE IF EXISTS `refund_order`;
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
) ENGINE=InnoDB AUTO_INCREMENT=2107689450410840068 DEFAULT CHARSET=utf8mb3 COMMENT='退款订单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
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

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
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

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
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

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
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

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `simulated_provider_payment`
--

DROP TABLE IF EXISTS `simulated_provider_payment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `simulated_provider_payment` (
  `pay_order_no` bigint NOT NULL,
  `amount` int NOT NULL,
  `status` int NOT NULL DEFAULT '1',
  `success_time` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`pay_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `simulated_provider_refund`
--

DROP TABLE IF EXISTS `simulated_provider_refund`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `simulated_provider_refund` (
  `refund_order_no` bigint NOT NULL,
  `pay_order_no` bigint NOT NULL,
  `amount` int NOT NULL,
  `status` int NOT NULL DEFAULT '2',
  PRIMARY KEY (`refund_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_search`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_search` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_search`;

--
-- Table structure for table `course_metadata_projection`
--

DROP TABLE IF EXISTS `course_metadata_projection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_metadata_projection` (
  `course_id` bigint NOT NULL,
  `version` bigint NOT NULL DEFAULT '1',
  `processed_version` bigint NOT NULL DEFAULT '0',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` char(36) DEFAULT NULL,
  `lease_until` datetime(3) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_error` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`course_id`),
  KEY `ix_metadata_due` (`status`,`next_attempt_at`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_sale_detail`
--

DROP TABLE IF EXISTS `course_sale_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_sale_detail` (
  `order_detail_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `active` tinyint NOT NULL,
  PRIMARY KEY (`order_detail_id`),
  KEY `idx_sale_course` (`course_id`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `course_sales_projection`
--

DROP TABLE IF EXISTS `course_sales_projection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_sales_projection` (
  `course_id` bigint NOT NULL,
  `sold` bigint NOT NULL,
  `version` bigint NOT NULL,
  `processed_version` bigint NOT NULL,
  `lease_token` varchar(36) DEFAULT NULL,
  `lease_until` datetime(3) DEFAULT NULL,
  `attempts` int NOT NULL DEFAULT '0',
  `last_error` varchar(500) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  PRIMARY KEY (`course_id`),
  KEY `idx_sales_projection_due` (`next_attempt_at`,`lease_until`),
  KEY `ix_sales_due` (`status`,`next_attempt_at`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `interests`
--

DROP TABLE IF EXISTS `interests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `interests` (
  `id` bigint NOT NULL COMMENT '主键，对应用户id',
  `interests` varchar(255) DEFAULT NULL COMMENT '感兴趣的二级分类id，以逗号分隔，例如：120,220,330',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户兴趣表，保存感兴趣的二级分类id';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
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

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
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

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
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

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
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

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_message`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_message` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_message`;

--
-- Table structure for table `message_template`
--

DROP TABLE IF EXISTS `message_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `message_template` (
  `id` bigint NOT NULL COMMENT '短信发送模板id',
  `name` varchar(50) NOT NULL COMMENT '模板名称',
  `platform_code` varchar(50) NOT NULL COMMENT '第三方短信平台代号',
  `sign_name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '签名',
  `third_template_code` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '第三方短信模板code',
  `content` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '第三方短信模板内容预览',
  `template_id` bigint NOT NULL COMMENT '通知模板id',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '模板状态:  0-禁用，1-启用',
  `creater` bigint NOT NULL COMMENT '创建者',
  `updater` bigint NOT NULL COMMENT '更新者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='第三方短信平台签名和模板信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notice_task`
--

DROP TABLE IF EXISTS `notice_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notice_task` (
  `id` bigint NOT NULL COMMENT '公告任务id',
  `template_id` bigint NOT NULL COMMENT '任务对应的通知模板id',
  `name` varchar(128) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '任务名称',
  `partial` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否是部分人的通告，默认false',
  `push_time` datetime DEFAULT NULL COMMENT '任务预期执行时间',
  `interval` int DEFAULT NULL COMMENT '任务延迟执行时间间隔，单位是分钟',
  `expire_time` datetime DEFAULT NULL COMMENT '任务失效时间',
  `max_times` int DEFAULT '1' COMMENT '任务重复执行次数上限，1则只发一次',
  `finished` bit(1) DEFAULT b'0' COMMENT '任务是否完成，默认false',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delivery_status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `delivery_attempts` int NOT NULL DEFAULT '0',
  `delivery_token` char(36) DEFAULT NULL,
  `delivery_next_attempt` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `delivery_error` varchar(1000) DEFAULT NULL,
  `delivery_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `ix_notice_due` (`finished`,`push_time`),
  KEY `ix_notice_delivery` (`delivery_status`,`delivery_next_attempt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='系统通告的任务表，可以延期或定期发送通告';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notice_task_target`
--

DROP TABLE IF EXISTS `notice_task_target`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notice_task_target` (
  `task_id` bigint NOT NULL COMMENT '任务id',
  `target_id` bigint NOT NULL COMMENT '目标用户id',
  PRIMARY KEY (`task_id`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通知任务的目标用户信息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `notice_template`
--

DROP TABLE IF EXISTS `notice_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notice_template` (
  `id` bigint NOT NULL COMMENT '通知模板id',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '通知模板名称',
  `code` varchar(50) NOT NULL COMMENT '模板代号，例如：verify-code',
  `type` tinyint NOT NULL COMMENT '通知类型：0-系统通知，1-笔记通知，2-问答通知，3-其它通知',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '模板状态:  0-草稿，1-使用中，2-停用',
  `title` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '通知标题，短信模板可以不填',
  `content` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '通知内容模板',
  `is_sms_template` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否是短信模板，默认false',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='通知模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `public_notice`
--

DROP TABLE IF EXISTS `public_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `public_notice` (
  `id` bigint NOT NULL COMMENT '公告id',
  `title` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '公告标题',
  `content` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '公告通知内容，可以存放公告消息模板',
  `type` tinyint NOT NULL COMMENT '通知类型：0-系统通知，1-笔记通知，2-问答通知，3-其它通知',
  `push_time` datetime NOT NULL COMMENT '通知发布时间',
  `expire_time` datetime NOT NULL COMMENT '通知失效时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='公告消息模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
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

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
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

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
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

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
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

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `simulated_sms`
--

DROP TABLE IF EXISTS `simulated_sms`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `simulated_sms` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` char(36) NOT NULL,
  `payload` json NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `task_id` (`task_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sms_delivery_task`
--

DROP TABLE IF EXISTS `sms_delivery_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sms_delivery_task` (
  `id` char(36) NOT NULL,
  `payload` json NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` char(36) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_error` varchar(1000) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `ix_sms_due` (`status`,`next_attempt_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sms_third_platform`
--

DROP TABLE IF EXISTS `sms_third_platform`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sms_third_platform` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '短信平台id',
  `name` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '短信平台名称',
  `code` varchar(50) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '短信平台代码，例如：ali',
  `priority` int unsigned NOT NULL DEFAULT '0' COMMENT '数字越小优先级越高，最小为0',
  `status` int NOT NULL DEFAULT '1' COMMENT '短信平台状态：0-禁用，1-启用',
  `creater` bigint NOT NULL COMMENT '创建人',
  `updater` bigint NOT NULL COMMENT '更新人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb3 COMMENT='第三方云通讯平台';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_inbox`
--

DROP TABLE IF EXISTS `user_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_inbox` (
  `id` bigint NOT NULL COMMENT '用户通知id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `type` tinyint DEFAULT '4' COMMENT '通知类型：0-系统通知，1-笔记通知，2-问答通知，3-其它通知，4-私信',
  `title` varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '' COMMENT '通知标题',
  `content` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '通知或私信内容',
  `is_read` bit(1) NOT NULL DEFAULT b'0' COMMENT '公告是否已读',
  `publisher` bigint NOT NULL DEFAULT '0' COMMENT '通知的发送者id，0则代表是系统',
  `push_time` datetime NOT NULL COMMENT '创建时间',
  `expire_time` datetime NOT NULL COMMENT '过期时间，一旦过期用户端不在展示',
  `public_notice_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uq_user_public_notice` (`user_id`,`public_notice_id`),
  KEY `user_id` (`user_id`),
  KEY `push_time` (`push_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COMMENT='用户通知记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Current Database: `tj_media`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `tj_media` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `tj_media`;

--
-- Table structure for table `file`
--

DROP TABLE IF EXISTS `file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file` (
  `id` bigint NOT NULL COMMENT '主键，文件id',
  `key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件在云端的唯一标示，例如：aaa.jpg',
  `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '文件上传时的名称',
  `request_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '请求id',
  `status` tinyint NOT NULL COMMENT '状态：1-待上传 2-已上传,未使用 3-已使用',
  `platform` tinyint DEFAULT '1' COMMENT '平台：1-腾讯，2-阿里',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除，默认0',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件表，可以是普通文件、图片等';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `media`
--

DROP TABLE IF EXISTS `media`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `media` (
  `id` bigint NOT NULL COMMENT '主键',
  `file_id` varchar(100) NOT NULL,
  `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '文件名称',
  `media_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '媒体播放地址',
  `cover_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT '媒体封面地址',
  `duration` double NOT NULL DEFAULT '0' COMMENT '视频时长，单位秒',
  `size` bigint NOT NULL DEFAULT '0' COMMENT '视频大小，单位是字节',
  `request_id` varchar(64) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-上传中，2-已上传',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `creater` bigint NOT NULL DEFAULT '0' COMMENT '创建者',
  `updater` bigint NOT NULL DEFAULT '0' COMMENT '更新者',
  `dep_id` bigint NOT NULL DEFAULT '0' COMMENT '部门id',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除，默认0',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uq_media_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='媒资表，主要是视频文件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `media_registration_guard`
--

DROP TABLE IF EXISTS `media_registration_guard`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `media_registration_guard` (
  `file_id` varchar(100) NOT NULL,
  PRIMARY KEY (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_consumer_failure`
--

DROP TABLE IF EXISTS `reliability_consumer_failure`;
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

--
-- Table structure for table `reliability_inbox`
--

DROP TABLE IF EXISTS `reliability_inbox`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reliability_inbox` (
  `consumer_name` varchar(128) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `reliability_operation`
--

DROP TABLE IF EXISTS `reliability_operation`;
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

--
-- Table structure for table `reliability_operation_failure`
--

DROP TABLE IF EXISTS `reliability_operation_failure`;
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

--
-- Table structure for table `reliability_outbox`
--

DROP TABLE IF EXISTS `reliability_outbox`;
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

--
-- Table structure for table `schema_migration`
--

DROP TABLE IF EXISTS `schema_migration`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schema_migration` (
  `version` varchar(128) NOT NULL,
  `checksum` char(64) NOT NULL,
  `applied_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `storage_cleanup_task`
--

DROP TABLE IF EXISTS `storage_cleanup_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `storage_cleanup_task` (
  `id` char(36) NOT NULL,
  `kind` varchar(16) NOT NULL,
  `object_key` varchar(255) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT '0',
  `lease_token` char(36) DEFAULT NULL,
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_error` varchar(1000) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `ix_cleanup_due` (`status`,`next_attempt_at`)
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

-- Dump completed on 2026-10-07 13:26:58
