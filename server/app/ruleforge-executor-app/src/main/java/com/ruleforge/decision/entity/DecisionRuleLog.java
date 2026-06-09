package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 决策规则执行明细表
 */
@Data
@TableName("nd_decision_rule_log")
public class DecisionRuleLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("flow_log_id")
    private Long flowLogId;

    @TableField("user_id")
    private String userId;

    @TableField("rule_node_index")
    private Integer ruleNodeIndex;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("rule_type")
    private String ruleType;

    @TableField("rule_index")
    private Integer ruleIndex;

    @TableField("rule_name")
    private String ruleName;

    @TableField("salience")
    private Integer salience;

    @TableField("activation_group")
    private String activationGroup;

    @TableField("agenda_group")
    private String agendaGroup;

    @TableField("ruleflow_group")
    private String ruleflowGroup;

    @TableField("lhs_condition")
    private String lhsCondition;

    @TableField("rhs_actions")
    private String rhsActions;

    @TableField("created_at")
    private Date createdAt;
}
