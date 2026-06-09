package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 仿真执行记录 — 对应 nd_simulation_run 表
 */
@Data
@TableName("nd_simulation_run")
public class SimulationRunEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("rule_package_path")
    private String rulePackagePath;

    @TableField("project")
    private String project;

    @TableField("package_id")
    private String packageId;

    @TableField("flow_id")
    private String flowId;

    @TableField("files")
    private String files;

    @TableField("start_time")
    private String startTime;

    @TableField("end_time")
    private String endTime;

    @TableField("batch_test_session_id")
    private Long batchTestSessionId;

    @TableField("status")
    private String status;

    @TableField("total_logs")
    private Integer totalLogs;

    @TableField("total_compared")
    private Integer totalCompared;

    @TableField("total_divergent")
    private Integer totalDivergent;

    @TableField("divergence_rate")
    private Double divergenceRate;

    @TableField("high_severity_count")
    private Integer highSeverityCount;

    @TableField("medium_severity_count")
    private Integer mediumSeverityCount;

    @TableField("low_severity_count")
    private Integer lowSeverityCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_by")
    private String createdBy;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;

    /** 状态常量 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_LOADING = "LOADING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPARING = "COMPARING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
