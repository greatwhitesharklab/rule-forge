-- 决策流执行日志表
-- V5.53: rename nd_ -> rfa_, 移到 migration-app/ (ruleforge_app_db)
-- V3.13.0a__rfa_decision_flow_log_gray_columns.sql 假设这张表已存在并 ALTER 加灰度相关列。

CREATE TABLE IF NOT EXISTS rfa_decision_flow_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT       NOT NULL COMMENT '项目ID',
    package_id      VARCHAR(200) NOT NULL COMMENT '知识包ID',
    flow_id         VARCHAR(200) NOT NULL COMMENT '决策流ID',
    request_data    MEDIUMTEXT   NULL     COMMENT '入参(序列化)',
    response_data   MEDIUMTEXT   NULL     COMMENT '出参(序列化)',
    status          VARCHAR(32)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED',
    error_message   TEXT         NULL     COMMENT '失败原因',
    exec_ms         BIGINT       NULL     COMMENT '耗时(ms)',
    git_tag         VARCHAR(200) NULL     COMMENT '执行的知识包版本',
    client_ip       VARCHAR(64)  NULL     COMMENT '客户端IP',
    user_id         VARCHAR(64)  NULL     COMMENT '调用用户ID',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_flow (project_id, flow_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策流执行日志';
