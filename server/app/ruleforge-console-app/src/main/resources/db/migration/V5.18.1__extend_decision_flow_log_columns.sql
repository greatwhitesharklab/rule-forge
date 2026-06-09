-- V5.18.1: 补全 nd_decision_flow_log 列,匹配 executor-app 的 DecisionFlowLog entity
--
-- 背景: DecisionFlowLog (com.ruleforge.decision.entity) 有 24 个字段,
-- 但 V3.12.5__create_nd_decision_flow_log.sql 只建了 13 列,V3.13.0 加了 3 个灰度列,
-- 仍是 16 列。每次 /api/loan/evaluate 触发 DecisionFlowLogService 写日志时
-- MyBatis-Plus 拿 entity 全字段 INSERT,会报
--   "Unknown column 'order_no' in 'field list'" / 'flow_version' / 'execution_status' / ...
--   一直到 8 个 missing column 全部跑完一遍才能定位真正缺什么。
--
-- 这张表原本可能是手工建在 dev 环境的,跟 nd_rule_variable_def 同样的历史遗留问题
-- (entity 跟 schema 走的是两条独立路径)。V5.18.0 修了变量定义表,这里修日志表。
--
-- 行为契约: DecisionFlowLog 所有 @TableField 字段都要有对应列才能写日志。
-- MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS`,用 information_schema 检查后 ALTER,
-- 老环境列已存在则跳过,不会破坏 V3.12.5 / V3.13.0 已建数据。

-- 18 个新列 — 每次启动跑一次(procedure 内部已存在列就退出)
DROP PROCEDURE IF EXISTS add_decision_flow_log_columns;
DELIMITER //
CREATE PROCEDURE add_decision_flow_log_columns()
BEGIN
    -- 请求/响应上下文
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'order_no') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN order_no VARCHAR(200) NULL COMMENT '业务订单号';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'flow_version') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN flow_version VARCHAR(64) NULL COMMENT '决策流版本';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'rule_package_path') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN rule_package_path VARCHAR(500) NULL COMMENT '规则包路径';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'rule_package_version') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN rule_package_version VARCHAR(64) NULL COMMENT '规则包版本';
    END IF;
    -- 决策结果
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'execution_status') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN execution_status VARCHAR(32) NULL COMMENT 'PASSED/REJECTED/ERROR';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'reject_reason') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN reject_reason VARCHAR(500) NULL COMMENT '拒绝原因';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'reject_code') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN reject_code VARCHAR(64) NULL COMMENT '拒绝错误码';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'node_names') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN node_names VARCHAR(1000) NULL COMMENT '触发的节点列表';
    END IF;
    -- 性能指标(ms)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'execution_time_ms') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN execution_time_ms BIGINT NULL COMMENT '规则执行耗时';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'total_time_ms') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN total_time_ms BIGINT NULL COMMENT '总耗时';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'load_knowledge_time_ms') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN load_knowledge_time_ms BIGINT NULL COMMENT '知识包加载耗时';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'flow_execution_time_ms') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN flow_execution_time_ms BIGINT NULL COMMENT '流程引擎耗时';
    END IF;
    -- 规则执行统计
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'total_matched_rules') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN total_matched_rules INT NULL COMMENT '命中规则数';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'total_fired_rules') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN total_fired_rules INT NULL COMMENT '触发规则数';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'total_loaded_fields') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN total_loaded_fields INT NULL COMMENT '加载字段数';
    END IF;
    -- 错误诊断
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'error_stack_trace') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN error_stack_trace TEXT NULL COMMENT '异常堆栈';
    END IF;
    -- 审计
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'created_at') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'nd_decision_flow_log' AND column_name = 'created_by') THEN
        ALTER TABLE nd_decision_flow_log ADD COLUMN created_by VARCHAR(64) NULL COMMENT '创建人';
    END IF;
END //
DELIMITER ;

CALL add_decision_flow_log_columns();
DROP PROCEDURE add_decision_flow_log_columns;

-- 老 schema 里 project_id / package_id 是 NOT NULL,但 entity 没有这俩字段
-- (projectId 在 v2 改用 rulePackagePath 字符串定位,packageId 同理),且运行时 INSERT
-- 不带它们 → "Field 'project_id' doesn't have a default value"。改 NULL 让老字段
-- 兼容,实际写入时这俩列就是 NULL,等数据回填/迁移完再考虑 DROP COLUMN。
ALTER TABLE nd_decision_flow_log MODIFY COLUMN project_id  BIGINT       NULL;
ALTER TABLE nd_decision_flow_log MODIFY COLUMN package_id  VARCHAR(200) NULL;

-- 索引
CREATE INDEX idx_decision_flow_log_order_no ON nd_decision_flow_log(order_no);
CREATE INDEX idx_decision_flow_log_user_flow ON nd_decision_flow_log(user_id, flow_id);
