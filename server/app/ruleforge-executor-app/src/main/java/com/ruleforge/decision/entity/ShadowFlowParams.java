package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 决策陪跑参数表
 */
@Data
@TableName("nd_decision_shadow_flow_params")
public class ShadowFlowParams {

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
