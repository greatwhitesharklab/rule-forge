-- V5.16.0 app_db 初始化迁移
--
-- 背景:V5.16 之前 app_db 没有 Flyway 管理,11 张 nd_* 表是 V5.1.x / V5.3.x
-- 各自在 app 启动 SQL 或手工脚本里建的(分布零散,版本不可追溯)。
-- V5.16 起:用 AppFlywayConfig 给 app_db 接入 Flyway,本文是第一个 migration,
-- 把现有 11 张表用 IF NOT EXISTS 落到这里 ——
--   * 新部署:从 0 跑起,11 张表统一创建
--   * 老部署:11 张表已存在但无 flyway_app_schema_history,首次启动时
--     AppFlywayConfig.baselineOnMigrate=true 在版本 0 建立 history,
--     然后 V5.16.0 跑(IF NOT EXISTS 让 DDL 幂等,实际不修改现有表)
--
-- 命名:用 V5.16.0 是为了跟 ruleforge_db 的 V5.15.0 保持递增,且两边用
-- 不同的 history 表(flyway_schema_history vs flyway_app_schema_history),
-- 不会互相干扰。
--
-- 注意:本文件只是 schema 状态"快照",**不**含业务数据回填,也不动 nd_*
-- 之外的表(如果有外部脚本建的表需要管控,后面另开 V5.16.x migration)。

-- AI 助手会话消息(每条消息一行)
CREATE TABLE IF NOT EXISTS `nd_agent_chat_message` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息 ID',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属会话 ID',
  `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色: system/user/assistant/tool',
  `content` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '消息内容',
  `tool_call_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具调用 ID',
  `tool_name` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AI 助手会话(一个调试会话 = 一行)
CREATE TABLE IF NOT EXISTS `nd_agent_chat_session` (
  `id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '会话 ID',
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话标题',
  `project` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属项目',
  `created_by` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AI 助手全局配置(key-value)
CREATE TABLE IF NOT EXISTS `nd_agent_config` (
  `config_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` text COLLATE utf8mb4_unicode_ci COMMENT '配置值',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置说明',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 告警历史(每次触发一行)
CREATE TABLE IF NOT EXISTS `nd_alert_history` (
  `id` bigint NOT NULL DEFAULT '0',
  `alert_rule_id` bigint NOT NULL,
  `rule_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发时的规则名称快照',
  `metric_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actual_value` double NOT NULL COMMENT '实际值',
  `threshold` double NOT NULL COMMENT '阈值',
  `webhook_url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `webhook_status` int DEFAULT NULL COMMENT 'Webhook HTTP状态码',
  `webhook_response` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Webhook响应（截断）',
  `fired_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 告警规则定义
CREATE TABLE IF NOT EXISTS `nd_alert_rule` (
  `id` bigint NOT NULL DEFAULT '0',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `metric_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '监控的指标名',
  `metric_tags` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'JSON标签过滤，null=全部',
  `condition` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'GT, LT, GTE, LTE, EQ',
  `threshold` double NOT NULL COMMENT '阈值',
  `duration_min` int NOT NULL DEFAULT '1' COMMENT '连续触发窗口数',
  `webhook_url` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Webhook 地址',
  `webhook_headers` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'JSON请求头，如 {"Authorization":"Bearer x"}',
  `cooldown_min` int NOT NULL DEFAULT '10' COMMENT '冷却时间（分钟）',
  `last_fired_at` datetime DEFAULT NULL COMMENT '上次触发时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 批量测试数据行
CREATE TABLE IF NOT EXISTS `nd_batch_test_row` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` bigint NOT NULL COMMENT '关联会话 ID',
  `row_index` int NOT NULL COMMENT '逻辑行号（从 1 开始）',
  `input_data` json NOT NULL COMMENT '原始输入数据 JSON',
  `output_data` json DEFAULT NULL COMMENT '执行结果 JSON',
  `error_message` varchar(500) DEFAULT NULL COMMENT '运行时错误信息',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/ERROR',
  `latency_ms` bigint DEFAULT NULL COMMENT '单条执行耗时 ms',
  `http_status` int DEFAULT NULL COMMENT 'HTTP 状态码(仅 DATASOURCE 模式有)',
  `error_code` varchar(64) DEFAULT NULL COMMENT '错误码(FLOW = RuleException label, DATASOURCE = HTTP/connector code)',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_session_status` (`session_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批量测试数据行';

-- 批量测试会话
CREATE TABLE IF NOT EXISTS `nd_batch_test_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project` varchar(100) NOT NULL COMMENT '项目名',
  `package_id` varchar(100) NOT NULL COMMENT '知识包 ID',
  `files` text COMMENT '规则文件路径（分号分隔）',
  `flow_id` varchar(255) DEFAULT NULL COMMENT '决策流 ID（可选）',
  `status` varchar(20) DEFAULT 'UPLOADED' COMMENT 'UPLOADED/RUNNING/COMPLETED/FAILED',
  `total_rows` int DEFAULT '0' COMMENT '总数据行数',
  `error_count` int DEFAULT '0' COMMENT '错误行数',
  `progress` double DEFAULT '0' COMMENT '执行进度 0.0~1.0',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `subject_type` varchar(32) NOT NULL DEFAULT 'FLOW' COMMENT 'FLOW / DATASOURCE — 测什么',
  `subject_id` bigint DEFAULT NULL COMMENT 'flowId 或 datasourceId,跟 subject_type 一起定位被测对象',
  `input_source_type` varchar(32) NOT NULL DEFAULT 'FILE' COMMENT 'FILE / DATASOURCE — input 从哪来',
  `input_source_id` bigint DEFAULT NULL COMMENT 'datasourceId(FLOW+DATASOURCE 或 DATASOURCE+DATASOURCE 时填)',
  `input_payload` mediumtext COMMENT '批量输入 JSON(FLOW+DATASOURCE 时存调三方拿到的 rows;FLOW+FILE 留空从 nd_batch_test_row 读)',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_batch_test_session_subject_type` (`subject_type`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批量测试会话';

-- 决策流执行日志(每个 execution 一行)
CREATE TABLE IF NOT EXISTS `nd_decision_flow_log` (
  `id` bigint NOT NULL DEFAULT '0',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `package_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '知识包ID',
  `flow_id` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '决策流ID',
  `request_data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '入参(序列化)',
  `response_data` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '出参(序列化)',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '失败原因',
  `exec_ms` bigint DEFAULT NULL COMMENT '耗时(ms)',
  `git_tag` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '执行的知识包版本',
  `client_ip` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '客户端IP',
  `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '调用用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_gray` tinyint DEFAULT '0' COMMENT '是否灰度流量',
  `gray_strategy_id` bigint DEFAULT NULL COMMENT '命中的灰度策略ID',
  `gray_git_tag` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '灰度使用的版本'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 指标快照(监控聚合后的 percentile / mean 落库,给监控面板查)
CREATE TABLE IF NOT EXISTS `nd_metrics_snapshot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `metric_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `metric_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tags` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `snapshot_time` datetime NOT NULL,
  `p50_ms` bigint DEFAULT NULL,
  `p95_ms` bigint DEFAULT NULL,
  `p99_ms` bigint DEFAULT NULL,
  `mean_ms` double DEFAULT NULL,
  `max_ms` bigint DEFAULT NULL,
  `min_ms` bigint DEFAULT NULL,
  `count_val` bigint DEFAULT NULL,
  `total_ms` double DEFAULT NULL,
  `gauge_val` double DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 仿真对比:模拟跑 vs 真实跑 字段级差异
CREATE TABLE IF NOT EXISTS `nd_simulation_result` (
  `id` bigint NOT NULL DEFAULT '0',
  `simulation_run_id` bigint NOT NULL COMMENT '关联 nd_simulation_run.id',
  `original_flow_log_id` bigint NOT NULL COMMENT '原始决策流日志 ID',
  `original_execution_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '原始执行状态',
  `original_reject_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '原始拒绝码',
  `original_output_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '原始输出参数 JSON',
  `original_rule_names` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '原始触发规则名 JSON 数组',
  `simulated_execution_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '模拟执行状态',
  `simulated_reject_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '模拟拒绝码',
  `simulated_output_params` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '模拟输出参数 JSON',
  `simulated_rule_names` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '模拟触发规则名 JSON 数组',
  `status_match` tinyint DEFAULT NULL COMMENT '执行状态是否一致',
  `result_match` tinyint DEFAULT NULL COMMENT '决策结果是否一致',
  `output_divergence` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '输出字段差异 JSON',
  `rule_divergence` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '规则执行差异 JSON',
  `has_divergence` tinyint DEFAULT '0' COMMENT '是否存在差异',
  `divergence_severity` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT 'NONE/LOW/MEDIUM/HIGH',
  `original_total_time_ms` bigint DEFAULT NULL COMMENT '原始执行耗时 ms',
  `simulated_total_time_ms` bigint DEFAULT NULL COMMENT '模拟执行耗时 ms',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '该行失败时的错误信息',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 仿真 run(一次仿真 = 一行,挂 N 个 result)
CREATE TABLE IF NOT EXISTS `nd_simulation_run` (
  `id` bigint NOT NULL DEFAULT '0',
  `rule_package_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则包路径 (project/packageId)',
  `project` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '项目名',
  `package_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '包 ID',
  `flow_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '决策流 ID（可选）',
  `files` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT '规则文件路径（分号分隔）',
  `start_time` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '历史数据起始时间',
  `end_time` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '历史数据结束时间',
  `batch_test_session_id` bigint DEFAULT NULL COMMENT '关联 nd_batch_test_session.id',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'PENDING' COMMENT 'PENDING/LOADING/RUNNING/COMPARING/COMPLETED/FAILED',
  `total_logs` int DEFAULT '0' COMMENT '加载的历史日志数',
  `total_compared` int DEFAULT '0' COMMENT '对比完成的条数',
  `total_divergent` int DEFAULT '0' COMMENT '存在差异的条数',
  `divergence_rate` double DEFAULT '0' COMMENT '差异率 0~100',
  `high_severity_count` int DEFAULT '0' COMMENT 'HIGH 严重度数量',
  `medium_severity_count` int DEFAULT '0' COMMENT 'MEDIUM 严重度数量',
  `low_severity_count` int DEFAULT '0' COMMENT 'LOW 严重度数量',
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '失败时的错误信息',
  `created_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '发起人',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
