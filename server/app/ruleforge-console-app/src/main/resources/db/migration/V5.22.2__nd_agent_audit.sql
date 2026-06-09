-- V5.22.2 Agent 审计 + 限流追踪表 (nd_agent_audit)
--
-- 设计原则:
-- - 审计每次 AgentController 的工具调用,给安全 / 成本 / 误用分析用
-- - 走 app_db (跟 nd_agent_chat_session / nd_agent_chat_message 同源)
-- - 不写 user LLM API 调用细节(隐私),只记 tool_name / args 摘要 / result_size / status
-- - session_id + user 是常用的复合索引
-- - 30 天后自动清理(应用层 job,不在 SQL 写 event)

CREATE TABLE IF NOT EXISTS nd_agent_audit (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY                                COMMENT '主键',
    session_id          VARCHAR(64)  DEFAULT NULL                                        COMMENT 'LLM 聊天会话 ID',
    message_id          VARCHAR(64)  DEFAULT NULL                                        COMMENT 'LLM 消息 ID',
    user_id             VARCHAR(64)  NOT NULL                                            COMMENT '调用人 (BA / agent 名)',
    tool_name           VARCHAR(128) NOT NULL                                            COMMENT '工具名 (draft_rule / list_drafts / ...)',
    args_summary        VARCHAR(500) DEFAULT NULL                                        COMMENT '参数摘要 (JSON 截前 500 字符,避免 MEDIUMTEXT 浪费)',
    result_size         INT          DEFAULT NULL                                        COMMENT '结果字节数',
    status              VARCHAR(20)  NOT NULL                                            COMMENT '状态: OK / ERROR / RATE_LIMITED',
    error_code          VARCHAR(64)  DEFAULT NULL                                        COMMENT '错误码 (validation_failed / tool_not_found / ...)',
    error_message       VARCHAR(500) DEFAULT NULL                                        COMMENT '错误摘要',
    duration_ms         BIGINT       DEFAULT NULL                                        COMMENT '执行耗时 (ms)',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                   COMMENT '调用时间',
    INDEX idx_audit_user_time (user_id, created_at),
    INDEX idx_audit_session (session_id),
    INDEX idx_audit_tool (tool_name, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 工具调用审计';
