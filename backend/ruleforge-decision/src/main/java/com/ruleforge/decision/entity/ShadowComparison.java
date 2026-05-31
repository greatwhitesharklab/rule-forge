package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 陪跑结果对比表
 */
@Data
@TableName("nd_decision_shadow_comparison")
public class ShadowComparison {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("main_flow_log_id")
    private Long mainFlowLogId;

    @TableField("shadow_flow_log_id")
    private Long shadowFlowLogId;

    @TableField("shadow_config_id")
    private Long shadowConfigId;

    @TableField("status_match")
    private Boolean statusMatch;

    @TableField("main_execution_status")
    private String mainExecutionStatus;

    @TableField("shadow_execution_status")
    private String shadowExecutionStatus;

    @TableField("result_match")
    private Boolean resultMatch;

    @TableField("main_reject_code")
    private String mainRejectCode;

    @TableField("shadow_reject_code")
    private String shadowRejectCode;

    @TableField("output_divergence")
    private String outputDivergence;

    @TableField("rule_divergence")
    private String ruleDivergence;

    @TableField("has_divergence")
    private Boolean hasDivergence;

    @TableField("divergence_severity")
    private String divergenceSeverity;

    @TableField("main_total_time_ms")
    private Long mainTotalTimeMs;

    @TableField("shadow_total_time_ms")
    private Long shadowTotalTimeMs;

    @TableField("user_id")
    private String userId;

    @TableField("order_no")
    private String orderNo;

    @TableField("rule_package_path")
    private String rulePackagePath;

    @TableField("created_at")
    private Date createdAt;
}
