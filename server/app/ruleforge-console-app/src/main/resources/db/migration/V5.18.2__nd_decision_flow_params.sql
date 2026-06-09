-- V5.18.2: 补建 nd_decision_flow_params 表
--
-- 背景: DecisionFlowParams (com.ruleforge.decision.entity) 7 个字段,
-- 这张表**从未**在任何 Flyway migration 里建过(entity 假设表存在,
-- executor 启动 OK,DecisionServiceImpl.execute() 一调就
-- "Table 'ruleforge_db.nd_decision_flow_params' doesn't exist")。
--
-- 同 nd_rule_variable_def (V5.18.0) / nd_decision_flow_log (V5.18.1)
-- 同样的历史遗留问题 — 实体在代码里,schema 只在 dev 手工建过,
-- 进了 git 但没进 Flyway。
--
-- 字段对齐 DecisionFlowParams entity 1:1 + 索引: (flow_log_id) 用于按日志查询参数。

CREATE TABLE IF NOT EXISTS nd_decision_flow_params (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    flow_log_id     BIGINT       NOT NULL                COMMENT '关联 nd_decision_flow_log.id',
    user_id         VARCHAR(64)  DEFAULT NULL            COMMENT '调用用户ID',
    input_params    MEDIUMTEXT   DEFAULT NULL            COMMENT '入参(序列化 JSON)',
    output_params   MEDIUMTEXT   DEFAULT NULL            COMMENT '出参(序列化 JSON)',
    entity_data     MEDIUMTEXT   DEFAULT NULL            COMMENT '决策引擎实体快照(序列化 JSON)',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_decision_flow_params_log (flow_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策流参数/实体数据(可后续迁到 DynamoDB/S3)';
