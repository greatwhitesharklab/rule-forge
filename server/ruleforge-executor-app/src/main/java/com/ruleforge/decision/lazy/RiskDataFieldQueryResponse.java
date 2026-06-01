package com.ruleforge.decision.lazy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 风控数据字段查询响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDataFieldQueryResponse {

    /**
     * 字段值
     */
    private Object fieldValue;

    /**
     * 数据类型
     * 可选值："STRING", "INTEGER", "DECIMAL", "BOOLEAN"
     */
    private String dataType;

    /**
     * 数据最后更新时间（字符串格式）
     */
    private String lastUpdateTime;

    /**
     * 是否来自缓存
     */
    private Boolean fromCache;

    /**
     * 数据源名称
     */
    private String dataSource;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 是否为新查询（调用了第三方API）
     */
    private Boolean newQuery;

    /**
     * 是否等待异步数据
     */
    private Boolean asyncPending;

    /**
     * 异步数据源ID
     */
    private String asyncDataSourceId;

    /**
     * 是否成功触发异步任务
     */
    private Boolean asyncTaskTriggered;
}
