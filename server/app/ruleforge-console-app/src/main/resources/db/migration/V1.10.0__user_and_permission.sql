-- V5.15.0 用户表 + 项目权限表 + 种子 admin
-- 把用户/权限从"文件+硬编码"迁移到 MySQL,实现 BCrypt 认证 + 项目级权限控制

-- 用户表
CREATE TABLE IF NOT EXISTS rf_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    company_id      VARCHAR(64)  DEFAULT 'ruleforge',
    is_admin        TINYINT(1)   DEFAULT 0,
    is_enabled      TINYINT(1)   DEFAULT 1,
    can_import      TINYINT(1)   DEFAULT 0,
    can_export      TINYINT(1)   DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 项目级权限表 (一个 user × 一个 project = 一行)
CREATE TABLE IF NOT EXISTS rf_user_project_permission (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                   BIGINT       NOT NULL,
    project                   VARCHAR(128) NOT NULL,
    read_project              TINYINT(1) DEFAULT 0,
    read_package              TINYINT(1) DEFAULT 0,
    write_package             TINYINT(1) DEFAULT 0,
    read_variable_file        TINYINT(1) DEFAULT 0,
    write_variable_file       TINYINT(1) DEFAULT 0,
    read_parameter_file       TINYINT(1) DEFAULT 0,
    write_parameter_file      TINYINT(1) DEFAULT 0,
    read_constant_file        TINYINT(1) DEFAULT 0,
    write_constant_file       TINYINT(1) DEFAULT 0,
    read_action_file          TINYINT(1) DEFAULT 0,
    write_action_file         TINYINT(1) DEFAULT 0,
    read_rule_file            TINYINT(1) DEFAULT 0,
    write_rule_file           TINYINT(1) DEFAULT 0,
    read_decision_table_file  TINYINT(1) DEFAULT 0,
    write_decision_table_file TINYINT(1) DEFAULT 0,
    read_decision_tree_file   TINYINT(1) DEFAULT 0,
    write_decision_tree_file  TINYINT(1) DEFAULT 0,
    read_scorecard_file       TINYINT(1) DEFAULT 0,
    write_scorecard_file      TINYINT(1) DEFAULT 0,
    read_flow_file            TINYINT(1) DEFAULT 0,
    write_flow_file           TINYINT(1) DEFAULT 0,
    UNIQUE KEY uk_user_project (user_id, project),
    CONSTRAINT fk_permission_user FOREIGN KEY (user_id) REFERENCES rf_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 种子:默认 admin (密码 admin123)
-- BCrypt hash generated at build time
INSERT INTO rf_user (username, password_hash, company_id, is_admin, is_enabled, can_import, can_export)
VALUES ('admin', '$2a$10$bXRAoEVRzWFpjbtA9IKYeOqTEwX.Nb.VEmR4SDe0XnMUVDXq/ZTRu', 'ruleforge', 1, 1, 1, 1);
