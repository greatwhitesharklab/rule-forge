-- V3.9.0: Multi-node deployment configuration
-- Executor nodes registration
CREATE TABLE IF NOT EXISTS gr_executor_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    node_name VARCHAR(128) NOT NULL UNIQUE COMMENT 'Display name for the executor node',
    node_url VARCHAR(512) NOT NULL COMMENT 'Base URL of the executor, e.g., http://192.168.1.10:8082',
    exec_env VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT 'Environment: prod, test, uat, etc.',
    status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT 'Node status: active, inactive',
    last_heartbeat DATETIME NULL COMMENT 'Last heartbeat timestamp from executor',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_exec_env (exec_env),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Registered executor nodes';

-- Deployment configuration: which package version is deployed to which node
CREATE TABLE IF NOT EXISTS gr_deployment_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL COMMENT 'References gr_project.id',
    package_id VARCHAR(64) NOT NULL COMMENT 'Package identifier within the project',
    executor_node_id BIGINT NULL COMMENT 'References gr_executor_node.id, null = all nodes in env',
    git_tag VARCHAR(128) NOT NULL COMMENT 'Git tag for the deployed version, e.g., pkg/pkg1/1.0.0',
    project_version VARCHAR(32) NOT NULL COMMENT 'Human-readable version number',
    exec_env VARCHAR(32) NOT NULL DEFAULT 'default' COMMENT 'Target environment',
    deploy_status VARCHAR(32) NOT NULL DEFAULT 'deployed' COMMENT 'deployed, failed, rolled_back',
    deploy_time DATETIME NULL COMMENT 'When this deployment was performed',
    deploy_user VARCHAR(64) NULL COMMENT 'User who triggered the deployment',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_project_package_env (project_id, package_id, exec_env),
    INDEX idx_executor_node (executor_node_id),
    INDEX idx_git_tag (git_tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Package deployment configuration per environment and executor node';
