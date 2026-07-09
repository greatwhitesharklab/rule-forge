-- V7.21 — BPMN 决策流后端彻底删除:DROP ruleforge_db 里的灰度策略表。
--
-- 安全策略:不删原建表迁移(V1.8.0),避免已执行环境的 Flyway checksum 失败,
-- 改用 forward 迁移做 DROP TABLE IF EXISTS。项目从未上生产。
--
-- 涉及表(灰度发布,后端代码已随 ruleforge-decision 模块删除):
--   rf_gray_strategy  (V1.8.0)

DROP TABLE IF EXISTS `rf_gray_strategy`;
