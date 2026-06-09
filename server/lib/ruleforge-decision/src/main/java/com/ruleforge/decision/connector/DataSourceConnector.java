package com.ruleforge.decision.connector;

import com.ruleforge.decision.entity.Datasource;

import java.util.Map;

/**
 * 数据源连接器接口
 * 每种数据源类型（ADVANCE_AI、REST_API、JDBC）实现此接口
 */
public interface DataSourceConnector {

    /**
     * 获取字段值
     *
     * @param datasource 数据源配置（从数据库读取）
     * @param entityId   实体唯一标识（如 userId）
     * @param clazz      实体类名
     * @param fieldName  字段名（已做字段映射转换）
     * @param context    请求上下文（loanZone、orbitCode 等）
     * @return 字段值
     */
    Object fetchFieldValue(Datasource datasource, String entityId, String clazz,
                           String fieldName, Map<String, String> context);

    /**
     * 预热：批量加载常用字段（JDBC 等支持批量查询的连接器可覆写）
     */
    default void warmUp(Datasource datasource, String entityId, String clazz,
                        Map<String, String> context, String... fieldNames) {
        // 默认逐个加载
    }

    /**
     * 测试数据源连通性
     *
     * @param datasource 数据源配置
     * @return true=连接成功
     */
    boolean testConnection(Datasource datasource);

    /**
     * 返回支持的连接器类型标识
     * 如 "ADVANCE_AI"、"REST_API"、"JDBC"
     */
    String getConnectorType();
}
