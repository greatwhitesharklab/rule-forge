package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 决策陪跑主流水表
 */
@Data
@TableName("nd_decision_shadow_flow_log")
public class ShadowFlowLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("main_flow_log_id")
    private Long mainFlowLogId;

    @TableField("user_id")
    private String userId;

    @TableField("order_no")
    private String orderNo;

    @TableField("flow_id")
    private String flowId;

    @TableField("flow_version")
    private String flowVersion;

    @TableField("rule_package_path")
    private String rulePackagePath;

    @TableField("rule_package_version")
    private String rulePackageVersion;

    @TableField("execution_status")
    private String executionStatus;

    @TableField("reject_reason")
    private String rejectReason;

    @TableField("reject_code")
    private String rejectCode;

    @TableField("node_names")
    private String nodeNames;

    @TableField("execution_time_ms")
    private Long executionTimeMs;

    @TableField("total_time_ms")
    private Long totalTimeMs;

    @TableField("load_knowledge_time_ms")
    private Long loadKnowledgeTimeMs;

    @TableField("flow_execution_time_ms")
    private Long flowExecutionTimeMs;

    @TableField("total_matched_rules")
    private Integer totalMatchedRules;

    @TableField("total_fired_rules")
    private Integer totalFiredRules;

    @TableField("total_loaded_fields")
    private Integer totalLoadedFields;

    @TableField("error_message")
    private String errorMessage;

    @TableField("error_stack_trace")
    private String errorStackTrace;

    @TableField("created_at")
    private Date createdAt;
}
