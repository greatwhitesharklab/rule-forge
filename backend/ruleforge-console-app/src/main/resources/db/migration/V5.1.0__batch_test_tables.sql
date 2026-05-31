-- V5.1.0: 批量测试数据持久化（替代 HttpSession 存储）
-- 批量测试会话：记录一次上传 + 执行的全生命周期
CREATE TABLE IF NOT EXISTS nd_batch_test_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project VARCHAR(100) NOT NULL COMMENT '项目名',
    package_id VARCHAR(100) NOT NULL COMMENT '知识包 ID',
    files TEXT COMMENT '规则文件路径（分号分隔）',
    flow_id VARCHAR(255) COMMENT '决策流 ID（可选）',
    status VARCHAR(20) DEFAULT 'UPLOADED' COMMENT 'UPLOADED/RUNNING/COMPLETED/FAILED',
    total_rows INT DEFAULT 0 COMMENT '总数据行数',
    error_count INT DEFAULT 0 COMMENT '错误行数',
    progress DOUBLE DEFAULT 0 COMMENT '执行进度 0.0~1.0',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量测试会话';

-- 测试数据行：每行一条输入数据 + 执行结果
CREATE TABLE IF NOT EXISTS nd_batch_test_row (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL COMMENT '关联会话 ID',
    row_index INT NOT NULL COMMENT '逻辑行号（从 1 开始）',
    input_data JSON NOT NULL COMMENT '原始输入数据 JSON',
    output_data JSON COMMENT '执行结果 JSON',
    error_message VARCHAR(500) COMMENT '运行时错误信息',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/ERROR',
    INDEX idx_session_id (session_id),
    INDEX idx_session_status (session_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批量测试数据行';
