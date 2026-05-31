-- 陪跑结果对比表
CREATE TABLE IF NOT EXISTS nd_decision_shadow_comparison (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    main_flow_log_id BIGINT NOT NULL COMMENT '主决策流日志ID',
    shadow_flow_log_id BIGINT NOT NULL COMMENT '陪跑决策流日志ID',
    shadow_config_id BIGINT NULL COMMENT '关联的陪跑配置ID',

    -- 执行状态对比
    status_match TINYINT DEFAULT NULL COMMENT '1=执行状态一致, 0=不一致',
    main_execution_status VARCHAR(20) NULL COMMENT '主流程执行状态',
    shadow_execution_status VARCHAR(20) NULL COMMENT '陪跑执行状态',

    -- 决策结果对比
    result_match TINYINT DEFAULT NULL COMMENT '1=决策结果一致, 0=不一致',
    main_reject_code VARCHAR(50) NULL COMMENT '主流程拒绝码',
    shadow_reject_code VARCHAR(50) NULL COMMENT '陪跑拒绝码',

    -- 输出字段差异明细 (JSON)
    -- 格式: [{"field":"creditLimit","main":"50000","shadow":"30000"}]
    output_divergence TEXT NULL COMMENT '输出字段差异明细',

    -- 规则执行差异明细 (JSON)
    -- 格式: {"onlyInMain":["ruleA"],"onlyInShadow":["ruleB"],"countDiff":{"mainMatched":5,"shadowMatched":3}}
    rule_divergence TEXT NULL COMMENT '规则执行差异明细',

    -- 汇总标记
    has_divergence TINYINT DEFAULT 0 COMMENT '1=有差异, 0=完全一致',
    divergence_severity VARCHAR(20) NULL COMMENT 'NONE/LOW/MEDIUM/HIGH',

    -- 耗时对比
    main_total_time_ms BIGINT NULL COMMENT '主流程总耗时',
    shadow_total_time_ms BIGINT NULL COMMENT '陪跑总耗时',

    -- 冗余便于查询
    user_id VARCHAR(100) NULL COMMENT '用户ID',
    order_no VARCHAR(100) NULL COMMENT '订单号',
    rule_package_path VARCHAR(500) NULL COMMENT '主规则包路径',

    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,

    UNIQUE INDEX idx_main_shadow (main_flow_log_id, shadow_flow_log_id),
    INDEX idx_divergence (has_divergence, created_at),
    INDEX idx_package_time (rule_package_path, created_at),
    INDEX idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='陪跑结果对比表';
