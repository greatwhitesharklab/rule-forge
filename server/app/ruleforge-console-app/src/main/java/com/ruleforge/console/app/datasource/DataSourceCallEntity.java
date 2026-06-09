package com.ruleforge.console.app.datasource;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * V5.23 — 数据源调用审计 (对应 nd_data_source_call 表, app_db).
 */
@Data
@TableName("nd_data_source_call")
public class DataSourceCallEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("data_source")
    private String dataSource;

    @TableField("inputs")
    private String inputs;

    @TableField("outputs")
    private String outputs;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("success")
    private Boolean success;

    @TableField("error_message")
    private String errorMessage;

    @TableField("call_time")
    private Date callTime;
}
