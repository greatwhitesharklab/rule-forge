package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 自建决策流执行器状态机(替代 Flowable ACT_RU_*)。
 * <p>
 * 模型:每条 evaluate 一行。同步主路径走完直接写 COMPLETED;异步节点(userTask 等)挂起写
 * WAITING_CALLBACK,@Scheduled 30s 扫一次恢复挂起超时的任务。结构照搬 nd_batch_test_session。
 */
@Data
@TableName("nd_decision_flow_state")
public class DecisionFlowState {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("flow_id")
    private String flowId;

    @TableField("flow_run_id")
    private String flowRunId;

    @TableField("user_id")
    private String userId;

    @TableField("order_no")
    private String orderNo;

    /** 状态枚举:PENDING / RUNNING / PENDING_ASYNC / WAITING_CALLBACK / COMPLETED / FAILED */
    @TableField("status")
    private String status;

    @TableField("current_node_id")
    private String currentNodeId;

    @TableField("current_node_type")
    private String currentNodeType;

    @TableField("next_retry_at")
    private Date nextRetryAt;

    @TableField("wait_ref")
    private String waitRef;

    @TableField("wait_type")
    private String waitType;

    @TableField("flow_xml_version")
    private String flowXmlVersion;

    @TableField("row_vars")
    private String rowVars;

    @TableField("row_entity_snapshot")
    private String rowEntitySnapshot;

    @TableField("output_model")
    private String outputModel;

    @TableField("progress")
    private Double progress;

    @TableField("error_message")
    private String errorMessage;

    @TableField("locked_by")
    private String lockedBy;

    @TableField("locked_at")
    private Date lockedAt;

    @TableField("locked_until")
    private Date lockedUntil;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("total_execution_ms")
    private Long totalExecutionMs;

    @TableField("fireable_rules")
    private Integer fireableRules;

    @TableField("matched_rules")
    private Integer matchedRules;

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;

    // 状态常量
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_PENDING_ASYNC = "PENDING_ASYNC";
    public static final String STATUS_WAITING_CALLBACK = "WAITING_CALLBACK";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
