package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 仿真对比结果 — 对应 nd_simulation_result 表
 */
@Data
@TableName("nd_simulation_result")
public class SimulationResultEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("simulation_run_id")
    private Long simulationRunId;

    @TableField("original_flow_log_id")
    private Long originalFlowLogId;

    // 原始决策
    @TableField("original_execution_status")
    private String originalExecutionStatus;

    @TableField("original_reject_code")
    private String originalRejectCode;

    @TableField("original_output_params")
    private String originalOutputParams;

    @TableField("original_rule_names")
    private String originalRuleNames;

    // 模拟决策
    @TableField("simulated_execution_status")
    private String simulatedExecutionStatus;

    @TableField("simulated_reject_code")
    private String simulatedRejectCode;

    @TableField("simulated_output_params")
    private String simulatedOutputParams;

    @TableField("simulated_rule_names")
    private String simulatedRuleNames;

    // 对比结果
    @TableField("status_match")
    private Boolean statusMatch;

    @TableField("result_match")
    private Boolean resultMatch;

    @TableField("output_divergence")
    private String outputDivergence;

    @TableField("rule_divergence")
    private String ruleDivergence;

    @TableField("has_divergence")
    private Boolean hasDivergence;

    @TableField("divergence_severity")
    private String divergenceSeverity;

    @TableField("original_total_time_ms")
    private Long originalTotalTimeMs;

    @TableField("simulated_total_time_ms")
    private Long simulatedTotalTimeMs;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_at")
    private Date createdAt;
}
