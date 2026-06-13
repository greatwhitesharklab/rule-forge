-- V5.22.1 AI 规则创作 — 草稿测试用例表 (rf_draft_test_case)
--
-- 设计原则:
-- - 1 个 draft 可以有 N 个测试用例
-- - BA 视角:在 AI 助手面板 → 草稿详情 → "测试用例" tab 增删改跑
-- - LLM 也能用 generate_test_cases 自动写一些
-- - inputs 是 JSON Map<String, Object> (决策表的入参变量)
-- - expected_row_id 是 LLM 推断 / BA 指定的"应该命中哪一行"
-- - 不强绑 rule_type — 不同规则的 inputs schema 不一样,只存 JSON
--
-- V5.22.1 放在 ruleforge_db (跟 rf_draft 同源)

CREATE TABLE IF NOT EXISTS rf_draft_test_case (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY                                COMMENT '主键',
    test_case_id        VARCHAR(64)  NOT NULL UNIQUE                                      COMMENT '对外可见 ID (UUID 短码)',
    draft_id            VARCHAR(64)  NOT NULL                                             COMMENT '关联的草稿 ID (rf_draft.draft_id)',
    name                VARCHAR(255) NOT NULL                                             COMMENT '测试用例名',
    description         TEXT         DEFAULT NULL                                         COMMENT '用例描述 (BA / LLM 说明)',
    inputs              MEDIUMTEXT   NOT NULL                                             COMMENT '入参 JSON (例: {"age":17,"income":5000})',
    expected_row_id     VARCHAR(64)  DEFAULT NULL                                         COMMENT '期望匹配的行 ID (NULL = 不要求匹配具体行)',
    created_by          VARCHAR(64)  NOT NULL                                             COMMENT '创建人 (BA / LLM)',
    source              VARCHAR(20)  NOT NULL DEFAULT 'MANUAL'                            COMMENT '来源: MANUAL / LLM',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP                   COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP                       COMMENT '更新时间',
    INDEX idx_tc_draft (draft_id),
    INDEX idx_tc_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 规则草稿的测试用例';
