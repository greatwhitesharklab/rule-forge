-- 灰度发布策略配置表
-- V5.53: rename gr_ -> rf_
CREATE TABLE IF NOT EXISTS rf_gray_strategy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    package_id VARCHAR(200) NOT NULL,
    strategy_name VARCHAR(200) NOT NULL COMMENT '策略名称',
    strategy_type VARCHAR(50) NOT NULL COMMENT 'PERCENT_USER / PERCENT_RANDOM / WHITELIST',
    gray_percent INT DEFAULT 0 COMMENT '灰度百分比 0-100, PERCENT_USER/PERCENT_RANDOM 时有效',
    whitelist TEXT NULL COMMENT '白名单用户ID, 逗号分隔, WHITELIST 时有效',
    target_git_tag VARCHAR(200) NOT NULL COMMENT '灰度目标版本',
    baseline_git_tag VARCHAR(200) NOT NULL COMMENT '基准生产版本',
    enabled TINYINT DEFAULT 1 COMMENT '1=启用 0=停用',
    description VARCHAR(500) NULL,
    created_by VARCHAR(100) NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pkg_enabled (project_id, package_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='灰度发布策略配置';

-- V5.53: 决策日志扩展灰度标记 — nd_decision_flow_log 部分已迁到
-- migration-app/V3.13.0a__rfa_decision_flow_log_gray_columns.sql(ruleforge_app_db)
