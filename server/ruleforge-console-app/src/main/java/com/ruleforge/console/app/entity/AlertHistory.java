package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("nd_alert_history")
public class AlertHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("alert_rule_id")
    private Long alertRuleId;

    @TableField("rule_name")
    private String ruleName;

    @TableField("metric_name")
    private String metricName;

    @TableField("actual_value")
    private Double actualValue;

    @TableField("threshold")
    private Double threshold;

    @TableField("webhook_url")
    private String webhookUrl;

    @TableField("webhook_status")
    private Integer webhookStatus;

    @TableField("webhook_response")
    private String webhookResponse;

    @TableField("fired_at")
    private Date firedAt;
}
