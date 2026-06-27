-- V7.6 V1 原生发布 — 已发布决策流登记表 (rf_v1_publish)
--
-- 解决问题:老 urule 的知识包(.rp)发布管线(PackageEditor 组装 → 审批 auditStatus 20/90
-- → 影子/批测 → refreshKnowledgeCache 推 executor)跟 V1 三处错配:
--   1. 组装的是 .rs.xml/.dt.xml 老类型,V7.5.2 已从 UI 删
--   2. V1FlowRunner 直接吃画布 JSON,不走"编译→打包→部署"
--   3. 审批/影子/批测对 V1 极简愿景是负担
--
-- V1 原生发布模型:每个 V1 决策流(.v1flow.json)独立可发布,状态只有 draft/published 两态。
-- 发布 = console 解析闭包(flow asset + 各节点 ruleRef 规则文件 + 项目库 vl/cl/pl)
--        → 序列化成不可变 bundle → 登记本表(当前发布版本)+ git 打 tag v1pub/{flow}/{ver}(可追溯)。
-- 生产执行(PR2):executor 拉 console 的 bundle → V1FlowRunner.execute(asset, fact, libraries, ruleFiles)。
--
-- 设计:
-- - 一流一行 (UNIQUE project+flow_path),再发布 = 覆盖 current_* + 更新 publish_bundle
-- - publish_bundle LONGTEXT 存 {asset, libraries, ruleFiles} JSON(执行快照,不可变)
-- - 历史 = git tags(v1pub/{flow}/{v1},{v2}…);本表只记当前(MVP,历史版本经 git tag 重建)
-- - 无行 = draft;有行 = published
--
-- ruleforge_db (跟 rf_file / rf_draft 同源)

CREATE TABLE IF NOT EXISTS rf_v1_publish (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY                                    COMMENT '主键',
    project           VARCHAR(128) NOT NULL                                                 COMMENT '项目名',
    flow_path         VARCHAR(512) NOT NULL                                                 COMMENT '决策流全路径 (/proj/V1决策流/x.v1flow.json)',
    current_version   VARCHAR(32)  NOT NULL                                                 COMMENT '当前发布版本号 (1.0.0)',
    current_git_tag   VARCHAR(128) DEFAULT NULL                                             COMMENT 'git tag (v1pub/{flow}/{version}),源码可追溯;无 git 仓时 NULL',
    publish_bundle    LONGTEXT     NOT NULL                                                 COMMENT '发布闭包 JSON ({asset, libraries, ruleFiles}),不可变执行快照',
    publish_user      VARCHAR(64)  DEFAULT NULL                                             COMMENT '发布人',
    publish_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                       COMMENT '发布时间',
    UNIQUE KEY uk_v1_publish_flow (project, flow_path),
    INDEX idx_v1_publish_project (project)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V1 已发布决策流登记(V7.6 原生发布,替代老 .rp 知识包)';
