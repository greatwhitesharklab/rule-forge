package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 决策流参数数据表
 * 存储出入参和实体数据，后续可迁移至 AWS DynamoDB / S3 等服务
 */
@Data
@TableName("nd_decision_flow_params")
public class DecisionFlowParams {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("flow_log_id")
    private Long flowLogId;

    @TableField("user_id")
    private String userId;

    @TableField("input_params")
    private String inputParams;

    @TableField("output_params")
    private String outputParams;

    @TableField("entity_data")
    private String entityData;

    @TableField("created_at")
    private Date createdAt;
}
