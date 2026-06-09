-- Phase 7: AgentScope 集成 — 配置、聊天会话和消息表
-- Flyway 迁移 V5.3.0

-- Agent 配置表（LLM API 配置存数据库，运行时可修改）
CREATE TABLE IF NOT EXISTS nd_agent_config (
    config_key      VARCHAR(128)    NOT NULL PRIMARY KEY COMMENT '配置键',
    config_value    TEXT            DEFAULT NULL COMMENT '配置值',
    description     VARCHAR(255)    DEFAULT NULL COMMENT '配置说明',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 配置';

-- 插入默认配置
INSERT INTO nd_agent_config (config_key, config_value, description) VALUES
    ('enabled',       'false',                        '是否启用 Agent 功能'),
    ('llm.vendor',    'openai',                       'LLM 厂商预设: openai/deepseek/qwen/zhipu/ollama/custom'),
    ('llm.base_url',  'https://api.openai.com/v1',    'LLM API 基础 URL（兼容 OpenAI Chat Completions 格式）'),
    ('llm.api_key',   '',                              'LLM API Key'),
    ('llm.model',     'gpt-4o',                        '模型名称'),
    ('llm.max_tokens','4096',                          '最大生成 token 数'),
    ('llm.temperature','0.7',                           '温度参数'),
    ('system_prompt', '你是 RuleForge 风控规则分析助手。你可以帮助用户：\n1. 分析决策日志和执行趋势\n2. 查看规则覆盖率，识别未触发的规则\n3. 检测异常决策模式\n4. 导出和查看规则包内容\n5. 查看监控指标和告警\n6. 分析仿真结果和对比数据\n\n请用中文回答。如果需要数据，请使用提供的工具查询。', '系统提示词');

-- 聊天会话表
CREATE TABLE IF NOT EXISTS nd_agent_chat_session (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY COMMENT '会话 ID',
    title           VARCHAR(255)    DEFAULT NULL COMMENT '会话标题',
    project         VARCHAR(128)    DEFAULT NULL COMMENT '所属项目',
    created_by      VARCHAR(128)    DEFAULT NULL COMMENT '创建人',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_session_project (project),
    INDEX idx_session_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 聊天会话';

-- 聊天消息表
CREATE TABLE IF NOT EXISTS nd_agent_chat_message (
    id              VARCHAR(64)     NOT NULL PRIMARY KEY COMMENT '消息 ID',
    session_id      VARCHAR(64)     NOT NULL COMMENT '所属会话 ID',
    role            VARCHAR(32)     NOT NULL COMMENT '角色: system/user/assistant/tool',
    content         MEDIUMTEXT      DEFAULT NULL COMMENT '消息内容',
    tool_call_id    VARCHAR(128)    DEFAULT NULL COMMENT '工具调用 ID',
    tool_name       VARCHAR(128)    DEFAULT NULL COMMENT '工具名称',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_message_session (session_id),
    INDEX idx_message_time (create_time),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES nd_agent_chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 聊天消息';
