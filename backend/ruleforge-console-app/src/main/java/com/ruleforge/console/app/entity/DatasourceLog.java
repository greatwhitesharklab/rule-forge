package com.ruleforge.console.app.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;

/**
 * 数据源调用日志（含缓存）
 */
@Data
@TableName("nd_datasource_log")
public class DatasourceLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    @TableField("datasource_id")
    private Long datasourceId;

    @TableField("data_source")
    private String dataSource;

    @TableField("api_endpoint")
    private String apiEndpoint;

    @TableField("request_method")
    private String requestMethod;

    @TableField("request_data")
    private String requestData;

    @TableField("response_data")
    private String responseData;

    @TableField("http_status")
    private Integer httpStatus;

    @TableField("status")
    private String status;

    @TableField("error_message")
    private String errorMessage;

    @TableField("response_time_ms")
    private Long responseTimeMs;

    @TableField("request_id")
    private String requestId;

    @TableField(value = "created_at", insertStrategy = FieldStrategy.NEVER)
    private Date createdAt;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
