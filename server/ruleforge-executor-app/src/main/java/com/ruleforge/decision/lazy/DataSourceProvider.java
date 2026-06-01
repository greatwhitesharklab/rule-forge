package com.ruleforge.decision.lazy;

/**
 * 数据源提供者接口
 * 用于延迟加载实体字段值
 */
public interface DataSourceProvider {

    /**
     * 获取指定实体的字段值
     *
     * @param entityId 实体唯一标识
     * @param clazz 实体类名（如 com.ruleforge.ADVModel）
     * @param fieldName 字段名
     * @return 字段值
     */
    Object fetchFieldValue(String entityId, String clazz, String fieldName);

    /**
     * 预热：批量加载常用字段（可选实现）
     *
     * @param entityId 实体唯一标识
     * @param clazz 实体类名
     * @param fieldNames 字段名列表
     */
    default void warmUp(String entityId, String clazz, String... fieldNames) {
        // 默认不实现
    }
}
