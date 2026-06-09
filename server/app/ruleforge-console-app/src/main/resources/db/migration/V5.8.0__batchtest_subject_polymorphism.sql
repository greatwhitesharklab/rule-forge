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
-- 幂等策略:用 information_schema 查列/索引是否存在,有就跳,
-- 没有就执行。这样重跑 / 部分应用场景都能恢复。
-- (MySQL 8.0 不支持 `ADD COLUMN IF NOT EXISTS` —— 那是 MariaDB 语法,
--  Oracle MySQL 一直没采纳,必须用 stored procedure 或预检查)

-- ── 准备:动态 SQL 帮手 ───────────────────────────────────────────
-- 用会话变量 + PREPARE/EXECUTE 来实现"列存在就跳"。
-- 每个 ADD COLUMN 块用一个独立的 stored procedure 模拟。

DROP PROCEDURE IF EXISTS v580_add_column_if_missing;
CREATE PROCEDURE v580_add_column_if_missing(
    IN p_table VARCHAR(64),
    IN p_col VARCHAR(64),
    IN p_def TEXT
)
BEGIN
    DECLARE v_exists INT DEFAULT 0;
    SELECT COUNT(*) INTO v_exists
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = p_table
      AND column_name = p_col;
    IF v_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_def);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END;

-- ── nd_batch_test_session 加列 ─────────────────────────────────────
CALL v580_add_column_if_missing('nd_batch_test_session', 'subject_type',
    'subject_type VARCHAR(32) NOT NULL DEFAULT ''FLOW'' COMMENT ''FLOW / DATASOURCE — 测什么''');
CALL v580_add_column_if_missing('nd_batch_test_session', 'subject_id',
    'subject_id BIGINT NULL COMMENT ''flowId 或 datasourceId,跟 subject_type 一起定位被测对象''');
CALL v580_add_column_if_missing('nd_batch_test_session', 'input_source_type',
    'input_source_type VARCHAR(32) NOT NULL DEFAULT ''FILE'' COMMENT ''FILE / DATASOURCE — input 从哪来''');
CALL v580_add_column_if_missing('nd_batch_test_session', 'input_source_id',
    'input_source_id BIGINT NULL COMMENT ''datasourceId(FLOW+DATASOURCE 或 DATASOURCE+DATASOURCE 时填)''');
CALL v580_add_column_if_missing('nd_batch_test_session', 'input_payload',
    'input_payload MEDIUMTEXT NULL COMMENT ''批量输入 JSON(FLOW+DATASOURCE 时存调三方拿到的 rows;FLOW+FILE 留空从 nd_batch_test_row 读)''');

-- ── nd_batch_test_row 加列 ────────────────────────────────────────
CALL v580_add_column_if_missing('nd_batch_test_row', 'latency_ms',
    'latency_ms BIGINT NULL COMMENT ''单条执行耗时 ms''');
CALL v580_add_column_if_missing('nd_batch_test_row', 'http_status',
    'http_status INT NULL COMMENT ''HTTP 状态码(仅 DATASOURCE 模式有)''');
CALL v580_add_column_if_missing('nd_batch_test_row', 'error_code',
    'error_code VARCHAR(64) NULL COMMENT ''错误码(FLOW = RuleException label, DATASOURCE = HTTP/connector code)''');

-- ── 索引(用同样的预检查)─────────────────────────────────────────
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'nd_batch_test_session'
                     AND index_name = 'idx_batch_test_session_subject_type');
SET @sql = IF(@idx_exists = 0,
              'CREATE INDEX idx_batch_test_session_subject_type ON nd_batch_test_session (subject_type, create_time)',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 清理:stored procedure 跑完即丢,不留
DROP PROCEDURE IF EXISTS v580_add_column_if_missing;

-- 现有数据的回填(让历史记录也能用新字段查):
--   subject_type 已经有 DEFAULT 'FLOW',不用改
--   input_source_type 同上
--   subject_id / input_source_id / latency_ms / http_status / error_code 留 NULL(语义上 = 未知)
--   input_payload 留 NULL(老 session 的 input 都在 nd_batch_test_row 里)
