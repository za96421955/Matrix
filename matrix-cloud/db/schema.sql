SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE DATABASE IF NOT EXISTS matrix DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE matrix;



-- =====================================================
-- OAuth 模块
-- =====================================================

-- 用户表（不分表）
DROP TABLE IF EXISTS `tbl_user_info`;
CREATE TABLE `tbl_user_info` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
`username` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户名',
`password_hash` varchar(256) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码哈希',
`auth_level` int NOT NULL DEFAULT '0' COMMENT '默认授权级别 (0-3)',
`email` varchar(128) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
`phone` varchar(32) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`creator` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'system' COMMENT '创建人 user_id',
`update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`updator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人 user_id',
`version_num` int NOT NULL DEFAULT '1' COMMENT '数据版本号',
`is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除，0：否；1：是',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_username` (`username`),
KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户信息表';

-- 用户访问密钥表（不分表）
DROP TABLE IF EXISTS `tbl_user_api_key`;
CREATE TABLE `tbl_user_api_key` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
`user_id` bigint NOT NULL COMMENT '用户ID',
`api_key` varchar(256) COLLATE utf8mb4_general_ci NOT NULL COMMENT '密码哈希',
`status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled/disabled',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`creator` varchar(64) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'system' COMMENT '创建人 user_id',
`update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`updator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人 user_id',
`version_num` int NOT NULL DEFAULT '1' COMMENT '数据版本号',
`is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除，0：否；1：是',
PRIMARY KEY (`id`),
KEY `idx_user_id` (`user_id`),
KEY `idx_api_key` (`api_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户访问密钥表';

-- 终端表（2 个分片）
DROP TABLE IF EXISTS `tbl_client_info_0000`;
CREATE TABLE `tbl_client_info_0000` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
`user_id` bigint NOT NULL COMMENT '用户 ID',
`client_id` varchar(128) COLLATE utf8mb4_general_ci NOT NULL COMMENT '终端 ID',
`type` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '终端类型：pc/iot/mobile',
`os_info` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '操作系统信息',
`status` varchar(16) COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'offline' COMMENT '状态：online/offline',
`secret` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '终端密钥',
`last_heartbeat` datetime DEFAULT NULL COMMENT '最后心跳时间',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`creator` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人 user_id',
`update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`updator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人 user_id',
`version_num` int NOT NULL DEFAULT '1' COMMENT '数据版本号',
`is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除，0：否；1：是',
PRIMARY KEY (`id`),
UNIQUE KEY `uk_client_id` (`client_id`),
KEY `idx_user_id` (`user_id`),
KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='终端表';

DROP TABLE IF EXISTS `tbl_client_info_0001`;
CREATE TABLE `tbl_client_info_0001` LIKE `tbl_client_info_0000`;



-- =====================================================
-- Chat 模块
-- =====================================================

-- 会话表（2 个分片）
DROP TABLE IF EXISTS `tbl_session_info_0000`;
CREATE TABLE `tbl_session_info_0000` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
`user_id` bigint NOT NULL COMMENT '用户 ID',
`title` varchar(256) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话标题',
`agent` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '会话 agent',
`auth_level` int NOT NULL DEFAULT '0' COMMENT '会话授权级别 (继承自 user)',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`creator` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人 user_id',
`update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`updator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人 user_id',
`version_num` int NOT NULL DEFAULT '1' COMMENT '数据版本号',
`is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除，0：否；1：是',
PRIMARY KEY (`id`),
KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='会话表';

DROP TABLE IF EXISTS `tbl_session_info_0001`;
CREATE TABLE `tbl_session_info_0001` LIKE `tbl_session_info_0000`;
DROP TABLE IF EXISTS `tbl_session_info_0002`;
CREATE TABLE `tbl_session_info_0002` LIKE `tbl_session_info_0000`;
DROP TABLE IF EXISTS `tbl_session_info_0003`;
CREATE TABLE `tbl_session_info_0003` LIKE `tbl_session_info_0000`;

-- 消息表（4 个分片，按 session_id 分片）
DROP TABLE IF EXISTS `tbl_message_info_0000`;
CREATE TABLE `tbl_message_info_0000` (
`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
`user_id` bigint NOT NULL COMMENT '用户 ID',
`session_id` bigint NOT NULL COMMENT '会话 ID',
`role` varchar(32) COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色：system/user/assistant/tool',
`content` text COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息内容',
`reasoning_content` text COLLATE utf8mb4_general_ci COMMENT '思考内容',
`tool_calls` json DEFAULT NULL COMMENT '工具调用',
`tool_call_id` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '工具调用ID',
`create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`creator` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '创建人 user_id',
`update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
`updator` varchar(64) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '更新人 user_id',
`version_num` int NOT NULL DEFAULT '1' COMMENT '数据版本号',
`is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除，0：否；1：是',
PRIMARY KEY (`id`),
KEY `idx_session_id` (`user_id`,`session_id`,`create_time`) USING BTREE,
KEY `idx_tool_call_id` (`tool_call_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='消息表';

DROP TABLE IF EXISTS `tbl_message_info_0001`;
CREATE TABLE `tbl_message_info_0001` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0002`;
CREATE TABLE `tbl_message_info_0002` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0003`;
CREATE TABLE `tbl_message_info_0003` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0004`;
CREATE TABLE `tbl_message_info_0004` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0005`;
CREATE TABLE `tbl_message_info_0005` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0006`;
CREATE TABLE `tbl_message_info_0006` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0007`;
CREATE TABLE `tbl_message_info_0007` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0008`;
CREATE TABLE `tbl_message_info_0008` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0009`;
CREATE TABLE `tbl_message_info_0009` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0010`;
CREATE TABLE `tbl_message_info_0010` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0011`;
CREATE TABLE `tbl_message_info_0011` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0012`;
CREATE TABLE `tbl_message_info_0012` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0013`;
CREATE TABLE `tbl_message_info_0013` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0014`;
CREATE TABLE `tbl_message_info_0014` LIKE `tbl_message_info_0000`;
DROP TABLE IF EXISTS `tbl_message_info_0015`;
CREATE TABLE `tbl_message_info_0015` LIKE `tbl_message_info_0000`;


