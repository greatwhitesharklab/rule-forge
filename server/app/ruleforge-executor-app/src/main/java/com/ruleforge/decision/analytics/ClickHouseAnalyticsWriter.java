package com.ruleforge.decision.analytics;

import com.ruleforge.decision.entity.DecisionFlowLog;
import com.ruleforge.decision.entity.DecisionRuleLog;
import com.ruleforge.decision.mapper.clickhouse.ChDecisionFlowLogMapper;
import com.ruleforge.decision.mapper.clickhouse.ChDecisionRuleLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 8: ClickHouse 双写异步消费者.
 *
 * <p>接收 {@link ClickHouseAnalyticsEvent},将 flow_log + rule_logs 写入 ClickHouse。
 * 所有异常被吞掉 + log.warn — ClickHouse 写入失败不影响 MySQL 主路径。
 *
 * <p>整个 bean 受 {@code clickhouse.analytics.enabled} 控制,禁用时不创建。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "clickhouse.analytics.enabled", havingValue = "true", matchIfMissing = true)
public class ClickHouseAnalyticsWriter {

    private final ChDecisionFlowLogMapper chFlowLogMapper;
    private final ChDecisionRuleLogMapper chRuleLogMapper;

    public ClickHouseAnalyticsWriter(
            @Autowired ChDecisionFlowLogMapper chFlowLogMapper,
            @Autowired ChDecisionRuleLogMapper chRuleLogMapper) {
        this.chFlowLogMapper = chFlowLogMapper;
        this.chRuleLogMapper = chRuleLogMapper;
    }

    @Async("clickhouseWriteExecutor")
    @EventListener
    public void onDecisionLogSaved(ClickHouseAnalyticsEvent event) {
        try {
            DecisionFlowLog flowLog = event.getFlowLog();
            chFlowLogMapper.insert(flowLog);

            List<DecisionRuleLog> ruleLogs = event.getRuleLogs();
            if (ruleLogs != null && !ruleLogs.isEmpty()) {
                int count = 0;
                for (DecisionRuleLog ruleLog : ruleLogs) {
                    try {
                        chRuleLogMapper.insert(ruleLog);
                        count++;
                    } catch (Exception e) {
                        log.warn("ClickHouse rule_log insert failed: flowLogId={}, ruleName={}: {}",
                                flowLog.getId(), ruleLog.getRuleName(), e.getMessage());
                    }
                }
                log.debug("ClickHouse dual-write: flowLogId={}, {} rule logs written", flowLog.getId(), count);
            }
        } catch (Exception e) {
            log.warn("ClickHouse dual-write failed for flowLogId={}: {}",
                    event.getFlowLog() != null ? event.getFlowLog().getId() : "null", e.getMessage());
        }
    }
}
