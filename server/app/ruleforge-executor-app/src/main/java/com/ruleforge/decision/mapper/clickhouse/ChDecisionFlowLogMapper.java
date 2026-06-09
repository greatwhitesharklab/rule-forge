package com.ruleforge.decision.mapper.clickhouse;

import com.ruleforge.decision.entity.DecisionFlowLog;
import org.apache.ibatis.annotations.Insert;

/**
 * Phase 8: ClickHouse 决策流日志写入 Mapper.
 *
 * <p>用 MySQL 的 DecisionFlowLog entity 复用字段映射,
 * ClickHouse ReplacingMergeTree 按 id 去重。
 */
public interface ChDecisionFlowLogMapper {

    @Insert("INSERT INTO nd_decision_flow_log " +
            "(id, user_id, order_no, flow_id, flow_version, rule_package_path, rule_package_version, " +
            " execution_status, reject_reason, reject_code, node_names, " +
            " execution_time_ms, total_time_ms, load_knowledge_time_ms, flow_execution_time_ms, " +
            " total_matched_rules, total_fired_rules, total_loaded_fields, " +
            " error_message, error_stack_trace, " +
            " is_gray, gray_strategy_id, gray_git_tag, created_at) " +
            "VALUES " +
            "(#{id}, #{userId}, #{orderNo}, #{flowId}, #{flowVersion}, #{rulePackagePath}, #{rulePackageVersion}, " +
            " #{executionStatus}, #{rejectReason}, #{rejectCode}, #{nodeNames}, " +
            " #{executionTimeMs}, #{totalTimeMs}, #{loadKnowledgeTimeMs}, #{flowExecutionTimeMs}, " +
            " #{totalMatchedRules}, #{totalFiredRules}, #{totalLoadedFields}, " +
            " #{errorMessage}, #{errorStackTrace}, " +
            " #{isGray}, #{grayStrategyId}, #{grayGitTag}, #{createdAt})")
    int insert(DecisionFlowLog entity);
}
