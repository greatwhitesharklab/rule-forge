package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 决策陪跑节点明细表
 */
@Data
@TableName("nd_decision_shadow_node_log")
public class ShadowNodeLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("flow_log_id")
    private Long flowLogId;

    @TableField("user_id")
    private String userId;

    @TableField("sort")
    private Integer sort;

    @TableField("decision_node_name")
    private String decisionNodeName;

    @TableField("decision_node_result")
    private String decisionNodeResult;

    @TableField("rule_node_name")
    private String ruleNodeName;

    @TableField("matched_rule_key")
    private String matchedRuleKey;

    @TableField("matched_rule_name")
    private String matchedRuleName;

    @TableField("matched_rule_action")
    private String matchedRuleAction;

    @TableField("created_at")
    private Date createdAt;
}
