package com.ruleforge.decision.lazy;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.decision.exception.AsyncDataSourcePendingException;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

/**
 * 延迟加载的通用实体
 * 继承 GeneralEntity，在访问字段时才从数据源加载数据
 */
@Slf4j
public class LazyGeneralEntity extends GeneralEntity {

    private final String entityId;
    private final DataSourceProvider dataSourceProvider;
    private final Set<String> loadedProperties;
    private final Set<String> loadingProperties;

    /**
     * 构造延迟加载实体
     *
     * @param targetClass 目标类名（如 com.ruleforge.ADVModel）
     * @param entityId 实体唯一标识
     * @param dataSourceProvider 数据源提供者
     */
    public LazyGeneralEntity(String targetClass, String entityId, DataSourceProvider dataSourceProvider) {
        super(targetClass);
        this.entityId = entityId;
        this.dataSourceProvider = dataSourceProvider;
        this.loadedProperties = new HashSet<>();
        this.loadingProperties = new HashSet<>();
    }

    /**
     * 重写 get 方法，实现延迟加载
     */
    @Override
    public Object get(Object key) {
        if (!(key instanceof String)) {
            return super.get(key);
        }

        String fieldName = (String) key;

        // 如果已经加载过，直接返回
        if (loadedProperties.contains(fieldName)) {
            return super.get(key);
        }

        // 防止循环加载
        if (loadingProperties.contains(fieldName)) {
            log.warn("Circular loading detected for field: {} in entity: {}", fieldName, getTargetClass());
            return null;
        }

        // 标记正在加载
        loadingProperties.add(fieldName);

        try {
            // 从数据源延迟加载
            log.debug("Lazy loading field: {} for entity: {} (id: {})",
                fieldName, getTargetClass(), entityId);

            Object value = dataSourceProvider.fetchFieldValue(entityId, getTargetClass(), fieldName);

            // 如果获取失败（返回 null），设置为 -999
            if (value == null) {
                log.warn("Data source returned null for field: {} in entity: {} (id: {}), setting to -999",
                    fieldName, getTargetClass(), entityId);
                value = -999;
            }

            // 缓存加载的值
            super.put(fieldName, value);
            loadedProperties.add(fieldName);

            log.debug("Loaded field: {} = {} for entity: {}", fieldName, value, getTargetClass());

            return value;
        } catch (AsyncDataSourcePendingException e) {
            // 异步等待异常需要向上传播，中止决策流程
            throw e;
        } catch (Exception e) {
            log.error("Failed to load field: {} for entity: {} (id: {}), setting to -999",
                fieldName, getTargetClass(), entityId, e);
            // 加载失败时设置为 -999
            super.put(fieldName, -999);
            loadedProperties.add(fieldName);
            return -999;
        } finally {
            loadingProperties.remove(fieldName);
        }
    }

    /**
     * 重写 put 方法，标记为已加载
     */
    @Override
    public Object put(String key, Object value) {
        loadedProperties.add(key);
        return super.put(key, value);
    }

    /**
     * 获取实体ID
     */
    public String getEntityId() {
        return entityId;
    }

    /**
     * 检查字段是否已加载
     */
    public boolean isFieldLoaded(String fieldName) {
        return loadedProperties.contains(fieldName);
    }

    /**
     * 获取已加载的字段集合
     */
    public Set<String> getLoadedFields() {
        return new HashSet<>(loadedProperties);
    }
}
