-- V5.23 数据源调用审计表 (nd_data_source_call)
--
-- 设计原则:
-- - 审计每次 DataSourceRegistry.fetch() 调用,给 SRE / BA 看第三方 API 行为用
-- - 走 app_db (跟 nd_agent_audit 同源 — 用同一个连接池)
-- - 入参 / 出参可能含 PII(身份证 / 手机号),应用层在 DataSourceAuditLogImpl 里 mask
-- - data_source + call_time 是常用索引(看某 DS 最近调用情况)
-- - 90 天后应用层清理(SQL 不写 event,跟 V5.22.2 nd_agent_audit 同模式)
--
-- 跟 V5.22.2 nd_agent_audit 区别:
-- - 那个是 Agent 工具调用(LLM 行为),这个是数据源调用(运行时行为)
-- - 频率高很多(每条决策都可能调多次第三方 API),所以索引更聚焦在 (data_source, call_time)
-- - duration_ms 直接打到调用本身(不含 framework interceptors 内部延迟,见 DataSourceRegistry)

CREATE TABLE IF NOT EXISTS nd_data_source_call (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY                                COMMENT '主键',
    data_source         VARCHAR(128) NOT NULL                                            COMMENT 'BaseApiDataSource.getName()',
    inputs              TEXT         DEFAULT NULL                                        COMMENT '输入 Vars (JSON, 应用层 mask PII)',
    outputs             TEXT         DEFAULT NULL                                        COMMENT '输出 Vars (JSON, 应用层 mask PII)',
    duration_ms         BIGINT       NOT NULL                                            COMMENT 'fetch() 耗时(框架 interceptors 也算在内)',
    success             TINYINT(1)   NOT NULL                                            COMMENT '1=成功 0=失败',
    error_message       VARCHAR(500) DEFAULT NULL                                        COMMENT '失败时错误摘要',
    call_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                   COMMENT '调用时间',
    INDEX idx_ds_time (data_source, call_time),
    INDEX idx_call_time (call_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='V5.23 数据源调用审计';
