package com.ruleforge.decision.lazy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 延迟加载实体工厂
 * 用于创建 LazyGeneralEntity 实例
 */
@Slf4j
@RequiredArgsConstructor
public class LazyEntityFactory {

    private final DataSourceProvider dataSourceProvider;

    /**
     * 创建延迟加载实体
     *
     * @param targetClass 目标类名（如 com.ruleforge.ADVModel）
     * @param entityId 实体唯一标识
     * @return LazyGeneralEntity 实例
     */
    public LazyGeneralEntity createLazyEntity(String targetClass, String entityId) {
        log.debug("Creating lazy entity: {} with id: {}", targetClass, entityId);
        return new LazyGeneralEntity(targetClass, entityId, dataSourceProvider);
    }

    /**
     * 创建延迟加载实体并预设一些字段值
     *
     * @param targetClass 目标类名
     * @param entityId 实体唯一标识
     * @param initialValues 初始值（这些值不会触发延迟加载）
     * @return LazyGeneralEntity 实例
     */
    public LazyGeneralEntity createLazyEntity(String targetClass, String entityId, java.util.Map<String, Object> initialValues) {
        LazyGeneralEntity entity = createLazyEntity(targetClass, entityId);

        if (initialValues != null && !initialValues.isEmpty()) {
            initialValues.forEach(entity::put);
            log.debug("Initialized entity {} with {} fields", targetClass, initialValues.size());
        }

        return entity;
    }
}
