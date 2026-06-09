-- URule 3.5.3 数据库升级脚本
-- 从3.1.4版本升级到3.5.3版本

-- 1. 修改gr_file表，添加缺失字段
ALTER TABLE gr_file
ADD COLUMN file_path varchar(512) NULL COMMENT '文件路径' AFTER file_type,
ADD COLUMN project_id bigint NULL COMMENT '项目ID' AFTER file_path,
ADD COLUMN latest_version_id bigint NULL COMMENT '最新版本ID' AFTER project_id;

-- 2. 修改gr_file_relation表，添加project_id字段
ALTER TABLE gr_file_relation
ADD COLUMN project_id bigint NULL COMMENT '项目ID' AFTER id;

-- 3. 修改gr_file_version表，添加缺失字段
ALTER TABLE gr_file_version
ADD COLUMN file_content longtext NULL COMMENT '文件内容' AFTER file_name,
ADD COLUMN project_version_num_real bigint NULL COMMENT '项目版本号(真实)' AFTER version_num_real,
ADD COLUMN project_id bigint NULL COMMENT '项目ID' AFTER audit_status;

-- 5. 创建知识包表
CREATE TABLE gr_package (
    id bigint AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    package_id varchar(64) NOT NULL COMMENT '知识包ID',
    name varchar(128) NULL COMMENT '知识包名称',
    resource_items text NULL COMMENT '资源项列表',
    project_id bigint NOT NULL COMMENT '项目ID',
    create_date datetime NULL COMMENT '创建时间',
    update_date datetime NULL COMMENT '更新时间',
    INDEX idx_package_id (package_id),
    INDEX idx_project_id (project_id)
) COMMENT='知识包表';

-- 6. 创建知识包版本表
CREATE TABLE gr_package_version (
    id bigint AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    package_id varchar(64) NOT NULL COMMENT '知识包ID',
    name varchar(128) NULL COMMENT '知识包名称',
    resource_items text NULL COMMENT '资源项列表',
    project_id bigint NOT NULL COMMENT '项目ID',
    version_num varchar(32) NULL COMMENT '版本号',
    version_num_real bigint NULL COMMENT '版本号(真实)',
    create_date datetime NULL COMMENT '创建时间',
    update_date datetime NULL COMMENT '更新时间',
    INDEX idx_package_id (package_id),
    INDEX idx_project_id (project_id)
) COMMENT='知识包版本表';

-- 7. 创建项目版本表
CREATE TABLE gr_project_version (
    id bigint AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    project_id bigint NOT NULL COMMENT '项目ID',
    package_id varchar(64) NULL COMMENT '知识包ID',
    version_name varchar(128) NULL COMMENT '版本名称',
    version_num_real bigint NULL COMMENT '版本号(真实)',
    audit_status int NULL DEFAULT 0 COMMENT '审核状态: 0-草稿,10-测试中,20-审批中,90-通过,91-拒绝',
    create_time datetime NULL COMMENT '创建时间',
    create_user varchar(64) NULL COMMENT '创建用户',
    comment text NULL COMMENT '备注',
    INDEX idx_project_id (project_id),
    INDEX idx_package_id (package_id)
) COMMENT='项目版本表';

-- 8. 创建项目版本映射表
CREATE TABLE gr_project_version_mapping (
    id bigint AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    project_version_id bigint NOT NULL COMMENT '项目版本ID',
    file_version_id bigint NOT NULL COMMENT '文件版本ID',
    project_id bigint NULL COMMENT '项目ID',
    file_id bigint NULL COMMENT '文件ID',
    INDEX idx_project_version_id (project_version_id),
    INDEX idx_file_version_id (file_version_id),
    INDEX idx_project_id (project_id)
) COMMENT='项目版本映射表';

-- 9. 创建项目运行时配置表
CREATE TABLE gr_project_runtime_config (
    id bigint AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    project_id bigint NOT NULL COMMENT '项目ID',
    package_id varchar(64) NULL COMMENT '知识包ID',
    project_version varchar(32) NULL COMMENT '项目版本',
    exec_env varchar(32) NULL COMMENT '执行环境',
    create_time datetime NULL COMMENT '创建时间',
    create_user varchar(64) NULL COMMENT '创建用户',
    update_time datetime NULL COMMENT '更新时间',
    update_user varchar(64) NULL COMMENT '更新用户',
    INDEX idx_project_id (project_id),
    INDEX idx_package_id (package_id)
) COMMENT='项目运行时配置表';

-- 10. 创建项目运行时流程表
CREATE TABLE gr_project_runtime_flow (
    id bigint AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    project_id bigint NOT NULL COMMENT '项目ID',
    package_id varchar(64) NULL COMMENT '知识包ID',
    project_version varchar(32) NULL COMMENT '项目版本',
    audit_status int NULL DEFAULT 0 COMMENT '审核状态: 0-草稿,20-审批中,90-通过,91-拒绝',
    exec_env varchar(32) NULL COMMENT '执行环境',
    proportion int NULL COMMENT '执行比例',
    start_time datetime NULL COMMENT '开始时间',
    end_time datetime NULL COMMENT '结束时间',
    create_time datetime NULL COMMENT '创建时间',
    create_user varchar(64) NULL COMMENT '创建用户',
    update_time datetime NULL COMMENT '更新时间',
    update_user varchar(64) NULL COMMENT '更新用户',
    INDEX idx_project_id (project_id),
    INDEX idx_package_id (package_id)
) COMMENT='项目运行时流程表';

-- 11. 创建项目导入流程表
CREATE TABLE gr_project_import_flow (
    id bigint AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name varchar(128) NULL COMMENT '流程名称',
    file_path varchar(512) NULL COMMENT '文件路径',
    create_user varchar(64) NULL COMMENT '创建用户',
    create_time datetime NULL COMMENT '创建时间',
    update_time datetime NULL COMMENT '更新时间',
    INDEX idx_create_user (create_user)
) COMMENT='项目导入流程表';

-- 12. 初始化数据：将gr_file表中file_type=1的记录迁移到gr_project表（如果还没有的话）
INSERT INTO gr_project(id, name, is_lock, create_time, update_time)
SELECT id, name, 0, create_time, NOW()
FROM gr_file
WHERE file_type = 1
AND id NOT IN (SELECT id FROM gr_project WHERE id IS NOT NULL);

-- 13. 更新gr_file表的project_id字段
UPDATE gr_file f
SET project_id = f.id
WHERE f.file_type = 1;

-- 14. 更新gr_file_relation表的project_id字段
UPDATE gr_file_relation fr
SET project_id = (
    SELECT f.project_id
    FROM gr_file f
    WHERE f.id = fr.descendant
)
WHERE EXISTS (
    SELECT 1
    FROM gr_file f
    WHERE f.id = fr.descendant
    AND f.project_id IS NOT NULL
);

-- 升级完成
-- 版本: 3.5.3
-- 日期: 2025-10-01

ALTER TABLE gr_file_version MODIFY COLUMN version_num varchar(128) NULL;
