-- V5.22.3 AI 规则创作 — 草稿状态历史 (rf_draft_history)
--
-- 解决问题:BA 想知道"这个草稿谁什么时候批的/拒的/改的/提交的"
-- 原来全记在 rf_draft.reviewed_by / reviewed_at / review_comment 三个字段里
-- 1) 不够 — 中间状态(SUBMIT)丢
-- 2) 不直观 — 不知道时间线
--
-- 设计:
-- - 每次状态转换插一行(action + from_status + to_status + actor + comment + 时间)
-- - 不删不改 — append-only 审计
-- - 跟 rf_draft 同 schema(ruleforge_db),同源 — 1:1 关联 draft_id
--
-- V5.22.3 跟着 V5.22.0 / V5.22.1 / V5.22.2 一起在 ruleforge_db

CREATE TABLE IF NOT EXISTS rf_draft_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY                                    COMMENT '主键',
    draft_id        VARCHAR(64)  NOT NULL                                                  COMMENT '关联 rf_draft.draft_id',
    action          VARCHAR(20)  NOT NULL                                                  COMMENT '动作: CREATE / SUBMIT / APPROVE / REJECT / APPLY / EDIT / EXPIRE',
    from_status     VARCHAR(20)  DEFAULT NULL                                              COMMENT '转换前状态',
    to_status       VARCHAR(20)  NOT NULL                                                  COMMENT '转换后状态',
    actor           VARCHAR(64)  DEFAULT NULL                                              COMMENT '操作人(用户/agent 名)',
    comment         TEXT         DEFAULT NULL                                              COMMENT '备注 (拒绝原因 / 审批意见 / 版本说明)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                       COMMENT '动作时间',
    INDEX idx_history_draft (draft_id, created_at),
    INDEX idx_history_action (action),
    INDEX idx_history_actor (actor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 规则草稿状态历史';
