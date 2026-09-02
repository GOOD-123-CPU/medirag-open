-- ============================================================
-- MediRAG 演示数据库初始化脚本
-- Database: medirag  |  Charset: utf8mb4
--
-- 说明：
-- 1. 本脚本仅包含【虚构演示数据】，不含任何真实个人信息。
-- 2. 演示账号密码均为公开测试密码（与 README 保持一致）：
--      admin   / Admin@123456   （管理员）
--      doctor1 / Doctor@123     （医护人员）
--      user1   / User@123456    （普通用户）
-- 3. 生产环境请删除演示账号并使用强密码策略。
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for med_conversation 对话会话表
-- ----------------------------
DROP TABLE IF EXISTS `med_conversation`;
CREATE TABLE `med_conversation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '关联用户ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '会话标题(首次提问自动生成)',
  `message_count` int NOT NULL DEFAULT 0 COMMENT '消息条数',
  `last_active` datetime NULL DEFAULT NULL COMMENT '最后活跃时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_last_active`(`last_active` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对话会话表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for med_feedback 用户反馈表
-- ----------------------------
DROP TABLE IF EXISTS `med_feedback`;
CREATE TABLE `med_feedback`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `message_id` bigint NOT NULL COMMENT '关联消息ID',
  `user_id` bigint NOT NULL COMMENT '评价用户ID',
  `rating` tinyint NOT NULL COMMENT '评分: 1有用 -1无用',
  `comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '补充说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_message_id`(`message_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户反馈表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for med_knowledge_base 医疗知识库文档表
-- ----------------------------
DROP TABLE IF EXISTS `med_knowledge_base`;
CREATE TABLE `med_knowledge_base`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档名称',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '文档描述',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '科室分类',
  `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '原始文件存储路径(MinIO)',
  `file_type` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '文件类型: pdf/docx/txt',
  `file_size` bigint NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `chunk_count` int NOT NULL DEFAULT 0 COMMENT '切片数量',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'uploading' COMMENT '状态: uploading/processing/ready/failed',
  `error_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_category`(`category` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '医疗知识库文档表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for med_message 对话消息表
-- ----------------------------
DROP TABLE IF EXISTS `med_message`;
CREATE TABLE `med_message`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` bigint NOT NULL COMMENT '关联会话ID',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色: user/assistant',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容(支持Markdown)',
  `sources` json NULL COMMENT '引用来源(文档名+章节+页码)',
  `retrieval_log` json NULL COMMENT '检索过程日志(用于可视化)',
  `feedback` tinyint NOT NULL DEFAULT 0 COMMENT '反馈: 1有用 -1无用 0未评',
  `is_fallback` tinyint NOT NULL DEFAULT 0 COMMENT '是否触发兜底回答',
  `tokens_used` int NULL DEFAULT NULL COMMENT 'Token消耗',
  `response_time` int NULL DEFAULT NULL COMMENT '响应时间(毫秒)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation_id`(`conversation_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对话消息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for med_query_log 问答查询日志表
-- ----------------------------
DROP TABLE IF EXISTS `med_query_log`;
CREATE TABLE `med_query_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户ID',
  `query` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始问题',
  `rewritten_query` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '改写后的问题',
  `department` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '涉及科室',
  `retrieve_count` int NULL DEFAULT NULL COMMENT '检索文档数',
  `rerank_top` int NULL DEFAULT NULL COMMENT '重排序Top数',
  `is_fallback` tinyint NOT NULL DEFAULT 0 COMMENT '是否兜底',
  `response_time_ms` int NULL DEFAULT NULL COMMENT '总响应时间(ms)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '查询时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_create_time`(`create_time` ASC) USING BTREE,
  INDEX `idx_department`(`department` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答查询日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_ai_config AI 配置表
-- ----------------------------
DROP TABLE IF EXISTS `sys_ai_config`;
CREATE TABLE `sys_ai_config`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `config_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `value_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'string',
  `group_name` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `label` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `description` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `default_value` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `min_value` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `max_value` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_config_key`(`config_key` ASC) USING BTREE
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user 系统用户表
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码(BCrypt加密)',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号',
  `nickname` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '头像URL',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '角色: admin/doctor/user',
  `health_profile` json NULL COMMENT '健康档案(JSON)',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1正常',
  `last_login` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0正常 1删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `uk_phone`(`phone` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户表' ROW_FORMAT = Dynamic;

-- ============================================================
-- 演示数据（全部为虚构内容）
-- ============================================================

-- 演示账号（密码均为公开测试密码，仅用于本地体验）
INSERT INTO `sys_user` (`id`, `username`, `password`, `phone`, `nickname`, `avatar`, `role`, `health_profile`, `status`) VALUES
(1, 'admin',   '$2a$10$4CH7ID2wtguhzfu0iV6kT.h/V33Vj1TxNi8C7Pkb89zu3RJp4X1K2', '13800000001', '系统管理员', NULL, 'admin',  NULL, 1),
(2, 'doctor1', '$2a$10$IxBCEmUOwrQ1VRHbv3Y3ye/QEZqF5SblCtPLDTA5zPmDiFIbCe5UK', '13800000002', '演示医生',   NULL, 'doctor', NULL, 1),
(3, 'user1',   '$2a$10$L418MNMtUCVlvHCzZ2qn2eQWTEYd1CSCxJAWTfMjDVnKZ8Ce6Apea', '13800000003', '演示用户',   NULL, 'user',   NULL, 1);

-- RAG / LLM / 缓存 / 安全阈值默认配置（与 application.yml 默认值对齐）
INSERT INTO `sys_ai_config` (`config_key`, `config_value`, `value_type`, `group_name`, `label`, `description`, `default_value`, `min_value`, `max_value`) VALUES
('rag.vector_top_k', '24', 'integer', 'rag', '向量召回 TopK', '向量检索返回的最大候选数量', '24', '5', '100'),
('rag.bm25_top_k', '24', 'integer', 'rag', '关键词召回 TopK', '关键词检索返回的最大候选数量', '24', '5', '100'),
('rag.rrf_top_n', '36', 'integer', 'rag', 'RRF 融合 TopN', 'RRF 融合后保留的最大数量', '36', '5', '200'),
('rag.rerank_top_k', '8', 'integer', 'rag', '重排序 TopK', 'Cross-Encoder 重排序后的最终数量', '8', '1', '20'),
('rag.rrf_k_constant', '60', 'integer', 'rag', 'RRF 常数 K', 'RRF 公式经验常数，值越大头部优势越小', '60', '1', '200'),
('llm.model', 'qwen-plus', 'string', 'llm', '对话模型', '对话生成使用的模型名称', 'qwen-plus', NULL, NULL),
('llm.streaming_temperature', '0.3', 'float', 'llm', '流式对话温度', '回答生成的随机性（0=确定性，1=随机）', '0.3', '0.0', '2.0'),
('llm.chat_temperature', '0.1', 'float', 'llm', '改写/检查温度', 'Query 改写和安全检查的温度参数', '0.1', '0.0', '2.0'),
('llm.timeout_seconds', '60', 'integer', 'llm', '请求超时(秒)', 'LLM 接口超时时间，流式加倍', '60', '10', '300'),
('cache.freq_threshold', '2', 'integer', 'cache', '缓存频次阈值', '同一问题被问几次后触发缓存写入', '2', '1', '100'),
('cache.ttl_hours', '2', 'integer', 'cache', '缓存 TTL(小时)', '缓存答案的保留时长', '2', '1', '168'),
('safety.confidence_threshold', '0.45', 'float', 'safety', '置信度阈值', '低于此值触发知识库未匹配兜底', '0.45', '0.0', '1.0');

SET FOREIGN_KEY_CHECKS = 1;
