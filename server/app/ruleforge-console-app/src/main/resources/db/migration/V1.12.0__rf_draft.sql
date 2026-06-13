-- V5.22.0 AI 规则创作 — draft 表 (rf_draft)
--
-- 设计原则:
-- - DRAFT 是 LLM 生成的规则"草稿",落地到 rf_ 权限域(ruleforge_db)里
-- - 跟 nd_agent_chat_session 共存,关联 sessionId / messageId 可审计
-- - 不存到项目存储里(那是要审批后才能入主仓的);审批通过再调 storage
-- - status 状态机:DRAFT → PENDING_REVIEW → (APPROVED|REJECTED|EXPIRED)
-- - content MEDIUMTEXT(决策表 JSON 通常 < 50KB,UL 脚本 < 100KB,够用)
--
-- V5.22.0 放在 ruleforge_db (跟 rf_user / rf_user_audit_log 同源)

CREATE TABLE IF NOT EXISTS rf_draft (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY                                    COMMENT '主键',
    draft_id            VARCHAR(64)  NOT NULL UNIQUE                                          COMMENT '对外可见 ID (UUID 短码)',
    session_id          VARCHAR(64)  DEFAULT NULL                                              COMMENT 'LLM 聊天会话 ID (V5.3 nd_agent_chat_session.id)',
    message_id          VARCHAR(64)  DEFAULT NULL                                              COMMENT 'LLM 消息 ID (V5.3 nd_agent_chat_message.id)',
    project             VARCHAR(200) NOT NULL                                                  COMMENT '项目名',
    package_path        VARCHAR(500) DEFAULT NULL                                              COMMENT '目标包路径 (审批后写入此处)',
    rule_type           VARCHAR(50)  NOT NULL                                                  COMMENT '规则类型: decision_table / ul / decision_tree / ...',
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'                                  COMMENT '状态: DRAFT/PENDING_REVIEW/APPROVED/REJECTED/EXPIRED',
    title               VARCHAR(255) DEFAULT NULL                                              COMMENT '草稿标题 (BA 视角)',
    content             MEDIUMTEXT   NOT NULL                                                  COMMENT '规则 JSON (跟 schema/{type}.json 一致)',
    source              VARCHAR(50)  NOT NULL DEFAULT 'LLM'                                    COMMENT '来源: LLM / CLI / MANUAL',
    source_meta         TEXT         DEFAULT NULL                                              COMMENT '来源元信息 (LLM 厂商、model、prompt 摘要等)',
    created_by          VARCHAR(64)  NOT NULL                                                  COMMENT '创建人 (用户/agent)',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                       COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    reviewed_by         VARCHAR(64)  DEFAULT NULL                                              COMMENT '审批人',
    reviewed_at         DATETIME     DEFAULT NULL                                              COMMENT '审批时间',
    review_comment      TEXT         DEFAULT NULL                                              COMMENT '审批意见 (拒绝原因 / 通过说明)',
    applied_version     VARCHAR(64)  DEFAULT NULL                                              COMMENT '审批后写入的版本号',
    applied_at          DATETIME     DEFAULT NULL                                              COMMENT '审批后写入时间',
    expires_at          DATETIME     DEFAULT NULL                                              COMMENT '过期时间 (DRAFT 超过 7 天自动 EXPIRED)',
    INDEX idx_draft_project (project),
    INDEX idx_draft_status (status),
    INDEX idx_draft_session (session_id),
    INDEX idx_draft_created_at (created_at),
    INDEX idx_draft_type (rule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 规则草稿';
