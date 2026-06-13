-- V5.19.0: 决策陪跑配置表(每条 rule_package 配一条主流程 + 陪跑流程)
-- V5.53.3: 加这张缺失的 migration。原 commit 漏写,entity @TableName("rfa_decision_shadow_config")
--          一直在指向不存在的表,DecisionServiceImpl.evaluate() → triggerShadowExecution →
--          shadowConfigService.findEnabledByMainPath 会触发 SQL 1146。运行时 silent fail
--          (triggerShadowExecution catch + log warn)所以一直没人发现,现在跑真流量会撞。
--
-- 背景: V5.19 引入「陪跑」机制 — 同一份入参,同时跑主规则包 + 陪跑规则包,对比结果写入
--       rfa_decision_shadow_comparison(V1.3.0)。配配置走 rfa_decision_shadow_config 本表。
--
-- 命名遵循 V{Major}.{Feature}.{Fix} 规范(CLAUDE.md)。

CREATE TABLE IF NOT EXISTS rfa_decision_shadow_config (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    main_rule_package_path   VARCHAR(500)  NOT NULL                          COMMENT '主规则包路径',
    shadow_rule_package_path VARCHAR(500)  NOT NULL                          COMMENT '陪跑规则包路径',
    shadow_flow_id           VARCHAR(200)  NULL                              COMMENT '陪跑流程 ID,空=跟主流程同 ID',
    enabled                  TINYINT       NOT NULL DEFAULT 1                COMMENT '0=停用,1=启用',
    sample_rate              INT           NOT NULL DEFAULT 100              COMMENT '采样率 0-100,100=每单都跑',
    created_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_main_path (main_rule_package_path),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='决策陪跑配置 — 每条主规则包配一条主+陪跑规则包';
