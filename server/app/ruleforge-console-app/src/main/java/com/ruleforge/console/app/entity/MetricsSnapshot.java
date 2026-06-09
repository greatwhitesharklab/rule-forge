package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("nd_metrics_snapshot")
public class MetricsSnapshot {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("metric_name")
    private String metricName;

    @TableField("metric_type")
    private String metricType;

    @TableField("tags")
    private String tags;

    @TableField("snapshot_time")
    private Date snapshotTime;

    @TableField("p50_ms")
    private Long p50Ms;

    @TableField("p95_ms")
    private Long p95Ms;

    @TableField("p99_ms")
    private Long p99Ms;

    @TableField("mean_ms")
    private Double meanMs;

    @TableField("max_ms")
    private Long maxMs;

    @TableField("min_ms")
    private Long minMs;

    @TableField("count_val")
    private Long countVal;

    @TableField("total_ms")
    private Double totalMs;

    @TableField("gauge_val")
    private Double gaugeVal;

    @TableField("created_at")
    private Date createdAt;
}
