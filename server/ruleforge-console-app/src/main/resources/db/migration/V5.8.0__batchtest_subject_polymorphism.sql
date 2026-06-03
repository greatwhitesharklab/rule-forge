-- V5.8.0: BatchTest 多态化(Subject × InputSource 二维矩阵)
--
-- 旧:BatchTest 单一 FLOW 模式,input 必须先 Excel 导入存到 nd_batch_test_row
-- 新:同一个表支持 3 种组合
--     subject (测什么)        ×  input_source (input 从哪来)
--     ──────────────────    ─────────────────────────
--     FLOW(决策流)            FILE (Excel 上传,沿用旧路径)
--                            DATASOURCE (调三方 API 取真实数据)
--     DATASOURCE(数据源)      DATASOURCE (直接拉接口 SLA,future)
--
-- 加列而不是新建表,避免双倍维护成本和"查全部 batch test" 跨表 join。

ALTER TABLE nd_batch_test_session
    ADD COLUMN subject_type      VARCHAR(32)  NOT NULL DEFAULT 'FLOW'
        COMMENT 'FLOW / DATASOURCE — 测什么',
    ADD COLUMN subject_id        BIGINT       NULL
        COMMENT 'flowId 或 datasourceId,跟 subject_type 一起定位被测对象',
    ADD COLUMN input_source_type VARCHAR(32)  NOT NULL DEFAULT 'FILE'
        COMMENT 'FILE / DATASOURCE — input 从哪来',
    ADD COLUMN input_source_id   BIGINT       NULL
        COMMENT 'datasourceId(FLOW+DATASOURCE 或 DATASOURCE+DATASOURCE 时填)',
    ADD COLUMN input_payload     MEDIUMTEXT   NULL
        COMMENT '批量输入 JSON(FLOW+DATASOURCE 时存调三方拿到的 rows;FLOW+FILE 留空从 nd_batch_test_row 读)';

ALTER TABLE nd_batch_test_row
    ADD COLUMN latency_ms  BIGINT       NULL
        COMMENT '单条执行耗时 ms',
    ADD COLUMN http_status INT          NULL
        COMMENT 'HTTP 状态码(仅 DATASOURCE 模式有)',
    ADD COLUMN error_code  VARCHAR(64)  NULL
        COMMENT '错误码(FLOW = RuleException label, DATASOURCE = HTTP/connector code)';

-- 索引:让按 subject_type 过滤的列表查询走索引(后续加 dashboard 时用得上)
CREATE INDEX idx_batch_test_session_subject_type
    ON nd_batch_test_session (subject_type, created_at);

-- 现有数据的回填(让历史记录也能用新字段查):
--   subject_type 已经有 DEFAULT 'FLOW',不用改
--   input_source_type 同上
--   subject_id / input_source_id / latency_ms / http_status / error_code 留 NULL(语义上 = 未知)
--   input_payload 留 NULL(老 session 的 input 都在 nd_batch_test_row 里)
