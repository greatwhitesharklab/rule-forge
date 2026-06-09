package com.ruleforge.decision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 决策陪跑消息明细表
 */
@Data
@TableName("nd_decision_shadow_message_log")
public class ShadowMessageLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("flow_log_id")
    private Long flowLogId;

    @TableField("user_id")
    private String userId;

    @TableField("msg_index")
    private Integer msgIndex;

    @TableField("msg_type")
    private String msgType;

    @TableField("msg")
    private String msg;

    @TableField("left_variable")
    private String leftVariable;

    @TableField("left_variable_value")
    private String leftVariableValue;

    @TableField("right_variable")
    private String rightVariable;

    @TableField("right_variable_value")
    private String rightVariableValue;

    @TableField("exec_time")
    private Date execTime;

    @TableField("created_at")
    private Date createdAt;
}
