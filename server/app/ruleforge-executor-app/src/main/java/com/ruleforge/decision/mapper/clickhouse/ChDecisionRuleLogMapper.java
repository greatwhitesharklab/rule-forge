package com.ruleforge.decision.mapper.clickhouse;

import com.ruleforge.decision.entity.DecisionRuleLog;
import org.apache.ibatis.annotations.Insert;

/**
 * Phase 8: ClickHouse 规则执行日志写入 Mapper.
 *
 * <p>用 MySQL 的 DecisionRuleLog entity 复用字段映射。
 * rule_type 在 CH 是 String (非 Nullable),空值时写入空串。
 */
public interface ChDecisionRuleLogMapper {

    @Insert("INSERT INTO nd_decision_rule_log " +
            "(id, flow_log_id, user_id, rule_node_index, duration_ms, rule_type, " +
            " rule_index, rule_name, salience, activation_group, agenda_group, ruleflow_group, " +
            " lhs_condition, rhs_actions, created_at) " +
            "VALUES " +
            "(#{id}, #{flowLogId}, #{userId}, #{ruleNodeIndex}, #{durationMs}, " +
            " CASE WHEN #{ruleType} IS NULL THEN '' ELSE #{ruleType} END, " +
            " #{ruleIndex}, #{ruleName}, #{salience}, #{activationGroup}, #{agendaGroup}, #{ruleflowGroup}, " +
            " #{lhsCondition}, #{rhsActions}, #{createdAt})")
    int insert(DecisionRuleLog entity);
}
