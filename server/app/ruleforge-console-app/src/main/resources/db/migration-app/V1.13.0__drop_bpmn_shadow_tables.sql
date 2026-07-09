-- V7.21 — BPMN 决策流后端彻底删除:DROP app_db (rfa_*) 里的 BPMN/陪跑表。
--
-- 安全策略:不删原建表迁移(避免已执行环境的 Flyway checksum 失败),改用 forward 迁移
-- 做 DROP TABLE IF EXISTS。项目从未上生产,这里只是收口 dev 库 schema。
--
-- 涉及表(均由 BPMN 决策流 / 陪跑对比写入,后端代码已删):
--   rfa_decision_flow_params    (V1.8.2)
--   rfa_decision_flow_state     (V1.9.0)
--   rfa_decision_shadow_comparison (V1.3.0)
--   rfa_decision_shadow_config  (V1.12.0)
-- (V1.11.0 是 rfa_decision_flow_state 的 ALTER ADD COLUMN,非独立表,随表一起 DROP)
--
-- 保留:
--   rfa_decision_flow_log       (V1.2.0 / V1.2.1 / V1.8.1)— 决策分析功能
--     (AnalysisController / DecisionAnalysisMapper / ClickHouseBackfillRunner)仍在线查询,
--     不 DROP(否则分析页 ClickHouse fallback 路径运行时报 Table doesn't exist)。
--   数据源/变量定义(rfa_datasource* / rfa_rule_variable_def)迁到 datasource 模块继续用。

DROP TABLE IF EXISTS `rfa_decision_flow_params`;
DROP TABLE IF EXISTS `rfa_decision_flow_state`;
DROP TABLE IF EXISTS `rfa_decision_shadow_comparison`;
DROP TABLE IF EXISTS `rfa_decision_shadow_config`;
