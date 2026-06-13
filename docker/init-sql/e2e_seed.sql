-- RuleForge E2E 测试 seed 数据
-- V5.50.8 — 在 docker mysql 启动时(01-init-databases.sql 之后)自动 seed
-- 命名空间 'E2E%' / 'e2e%' 前缀,跟 prod data 完全分离,真要误跑也不会污染
-- 用 INSERT IGNORE 保持幂等 — 重跑不会 duplicate key 报错

USE ruleforge_app_db;

-- E2E测试待编辑数据源:给 datasource-panel.spec.ts L174 那个 "edit 名称" 测试用
INSERT IGNORE INTO rfa_datasource (name, type, config_json, enabled, description, timeout_ms, cache_enabled, cache_ttl_hours)
VALUES ('E2E待编辑数据源', 'REST_API',
        '{"baseUrl":"https://example.com","method":"GET","headers":{},"params":{}}',
        1, 'E2E 测试 seed 数据源,给编辑用例用', 30000, 0, 120);
