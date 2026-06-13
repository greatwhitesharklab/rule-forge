-- V5.53: 从 V3.13.0__gray_strategy.sql 拆出
-- 决策日志扩展灰度标记(rfa_decision_flow_log 在 ruleforge_app_db)

ALTER TABLE rfa_decision_flow_log
    ADD COLUMN is_gray TINYINT DEFAULT 0 COMMENT '是否灰度流量',
    ADD COLUMN gray_strategy_id BIGINT DEFAULT NULL COMMENT '命中的灰度策略ID',
    ADD COLUMN gray_git_tag VARCHAR(200) DEFAULT NULL COMMENT '灰度使用的版本';
