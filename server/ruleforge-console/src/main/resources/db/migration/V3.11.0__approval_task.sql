-- Internal approval tasks for version deployment
CREATE TABLE IF NOT EXISTS gr_approval_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    package_id VARCHAR(64) NULL,
    project_version VARCHAR(32) NOT NULL,
    exec_env VARCHAR(32) NOT NULL DEFAULT 'prod',
    approval_type VARCHAR(32) NOT NULL DEFAULT 'prod_deploy',
    title VARCHAR(256) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    remark TEXT NULL,
    explain_text TEXT NULL,
    requester VARCHAR(64) NOT NULL,
    approver VARCHAR(64) NULL,
    approve_time DATETIME NULL,
    approve_remark TEXT NULL,
    process_id VARCHAR(128) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project_version_env (project_id, project_version, exec_env),
    INDEX idx_status (status),
    INDEX idx_requester (requester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Internal approval tasks for version deployment';
