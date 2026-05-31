-- V5.1.1: Rule Simulation tables (Phase 5 续)

-- 仿真执行记录
CREATE TABLE IF NOT EXISTS nd_simulation_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_package_path VARCHAR(500) NOT NULL COMMENT '规则包路径 (project/packageId)',
    project VARCHAR(100) NOT NULL COMMENT '项目名',
    package_id VARCHAR(100) NOT NULL COMMENT '包 ID',
    flow_id VARCHAR(255) COMMENT '决策流 ID（可选）',
    files TEXT COMMENT '规则文件路径（分号分隔）',
    start_time VARCHAR(30) NOT NULL COMMENT '历史数据起始时间',
    end_time VARCHAR(30) NOT NULL COMMENT '历史数据结束时间',
    batch_test_session_id BIGINT COMMENT '关联 nd_batch_test_session.id',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/LOADING/RUNNING/COMPARING/COMPLETED/FAILED',
    total_logs INT DEFAULT 0 COMMENT '加载的历史日志数',
    total_compared INT DEFAULT 0 COMMENT '对比完成的条数',
    total_divergent INT DEFAULT 0 COMMENT '存在差异的条数',
    divergence_rate DOUBLE DEFAULT 0 COMMENT '差异率 0~100',
    high_severity_count INT DEFAULT 0 COMMENT 'HIGH 严重度数量',
    medium_severity_count INT DEFAULT 0 COMMENT 'MEDIUM 严重度数量',
    low_severity_count INT DEFAULT 0 COMMENT 'LOW 严重度数量',
    error_message VARCHAR(500) COMMENT '失败时的错误信息',
    created_by VARCHAR(100) COMMENT '发起人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_package_time (rule_package_path, created_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仿真执行记录';

-- 逐条对比结果
CREATE TABLE IF NOT EXISTS nd_simulation_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    simulation_run_id BIGINT NOT NULL COMMENT '关联 nd_simulation_run.id',
    original_flow_log_id BIGINT NOT NULL COMMENT '原始决策流日志 ID',
    -- 原始决策
    original_execution_status VARCHAR(20) COMMENT '原始执行状态',
    original_reject_code VARCHAR(50) COMMENT '原始拒绝码',
    original_output_params TEXT COMMENT '原始输出参数 JSON',
    original_rule_names TEXT COMMENT '原始触发规则名 JSON 数组',
    -- 模拟决策
    simulated_execution_status VARCHAR(20) COMMENT '模拟执行状态',
    simulated_reject_code VARCHAR(50) COMMENT '模拟拒绝码',
    simulated_output_params TEXT COMMENT '模拟输出参数 JSON',
    simulated_rule_names TEXT COMMENT '模拟触发规则名 JSON 数组',
    -- 对比结果
    status_match TINYINT COMMENT '执行状态是否一致',
    result_match TINYINT COMMENT '决策结果是否一致',
    output_divergence TEXT COMMENT '输出字段差异 JSON',
    rule_divergence TEXT COMMENT '规则执行差异 JSON',
    has_divergence TINYINT DEFAULT 0 COMMENT '是否存在差异',
    divergence_severity VARCHAR(20) COMMENT 'NONE/LOW/MEDIUM/HIGH',
    original_total_time_ms BIGINT COMMENT '原始执行耗时 ms',
    simulated_total_time_ms BIGINT COMMENT '模拟执行耗时 ms',
    error_message VARCHAR(500) COMMENT '该行失败时的错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_simulation_run (simulation_run_id),
    INDEX idx_divergence (simulation_run_id, has_divergence),
    INDEX idx_original_log (original_flow_log_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仿真对比结果';
