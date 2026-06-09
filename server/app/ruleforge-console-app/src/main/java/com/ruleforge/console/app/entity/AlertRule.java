package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("nd_alert_rule")
public class AlertRule {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("name")
    private String name;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("metric_name")
    private String metricName;

    @TableField("metric_tags")
    private String metricTags;

    @TableField("`condition`")
    private String condition;

    @TableField("threshold")
    private Double threshold;

    @TableField("duration_min")
    private Integer durationMin;

    @TableField("webhook_url")
    private String webhookUrl;

    @TableField("webhook_headers")
    private String webhookHeaders;

    @TableField("cooldown_min")
    private Integer cooldownMin;

    @TableField("last_fired_at")
    private Date lastFiredAt;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;
}
