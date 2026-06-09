package com.ruleforge.decision.analytics;

import com.ruleforge.decision.entity.DecisionFlowLog;
import com.ruleforge.decision.entity.DecisionRuleLog;

import java.util.List;

/**
 * Phase 8: 决策日志双写到 ClickHouse 的事件 POJO.
 *
 * <p>由 {@link com.ruleforge.decision.service.impl.DecisionLogServiceImpl} 在 MySQL 写入成功后发布,
 * 由 {@link ClickHouseAnalyticsWriter} 异步消费写入 ClickHouse。
 */
public class ClickHouseAnalyticsEvent {

    private final DecisionFlowLog flowLog;
    private final List<DecisionRuleLog> ruleLogs;

    public ClickHouseAnalyticsEvent(DecisionFlowLog flowLog, List<DecisionRuleLog> ruleLogs) {
        this.flowLog = flowLog;
        this.ruleLogs = ruleLogs;
    }

    public DecisionFlowLog getFlowLog() {
        return flowLog;
    }

    public List<DecisionRuleLog> getRuleLogs() {
        return ruleLogs;
    }
}
