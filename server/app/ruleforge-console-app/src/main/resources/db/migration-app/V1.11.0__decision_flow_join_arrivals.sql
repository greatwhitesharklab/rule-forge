-- V5.33.0: rfa_decision_flow_state 加 join_arrivals JSON 列
-- V5.53: rename nd_ -> rfa_, 移到 migration-app/ (ruleforge_app_db)
--
-- 背景: V5.33 A0 mirror Rust V5.28 P6 ParallelGateway JOIN 严格化到 Java 端。Java 端
--       改 worklist 多 token 模型后,需要持久化 join 计数(join_target_id → arrived_count),
--       resume 时反序列化回 ctx.joinArrivals。
--
-- 命名遵循 V{Major}.{Feature}.{Fix} 规范(CLAUDE.md)。
--
-- 已发布 schema 不可改:只 ADD COLUMN,不改任何已有列。

ALTER TABLE rfa_decision_flow_state
    ADD COLUMN join_arrivals JSON NULL
    COMMENT 'V5.33 A0 — fork/join 计数 JSON, 格式 {"join_node_id": arrived_count}';
