package com.ruleforge.console.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.console.app.connector.DataSourceConnector;
import com.ruleforge.console.app.entity.Datasource;
import com.ruleforge.console.app.entity.DatasourceEntityMapping;
import com.ruleforge.console.app.entity.DatasourceFieldMapping;
import com.ruleforge.console.app.mapper.DatasourceEntityMappingMapper;
import com.ruleforge.console.app.mapper.DatasourceFieldMappingMapper;
import com.ruleforge.console.app.mapper.DatasourceMapper;
import com.ruleforge.console.app.service.IDatasourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 数据源管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceServiceImpl implements IDatasourceService {

    private final DatasourceMapper datasourceMapper;
    private final DatasourceEntityMappingMapper entityMappingMapper;
    private final DatasourceFieldMappingMapper fieldMappingMapper;
    private final List<DataSourceConnector> connectors;

    // ===== 字段映射内存缓存 =====
    private final ConcurrentHashMap<String, Map<String, String>> fieldMappingCache = new ConcurrentHashMap<>();

    // ===== 数据源 CRUD =====

    @Override
    public List<Datasource> listDatasources() {
        return datasourceMapper.selectList(null);
    }

    @Override
    public Datasource getDatasourceById(Long id) {
        return datasourceMapper.selectById(id);
    }

    @Override
    public Datasource createDatasource(Datasource datasource) {
        datasource.setCreatedAt(null);
        datasource.setUpdatedAt(null);
        datasourceMapper.insert(datasource);
        return datasource;
    }

    @Override
    public Datasource updateDatasource(Datasource datasource) {
        datasourceMapper.updateById(datasource);
        // 清除字段映射缓存
        evictFieldMappingCache(datasource.getId());
        return datasource;
    }

    @Override
    public void deleteDatasource(Long id) {
        datasourceMapper.deleteById(id);
        evictFieldMappingCache(id);
    }

    @Override
    public boolean testConnection(Long id) {
        Datasource ds = datasourceMapper.selectById(id);
        if (ds == null || !Boolean.TRUE.equals(ds.getEnabled())) {
            return false;
        }
        DataSourceConnector connector = resolveConnector(ds.getType());
        if (connector == null) {
            return false;
        }
        try {
            return connector.testConnection(ds);
        } catch (Exception e) {
            log.error("测试数据源连接失败: id={}, type={}", id, ds.getType(), e);
            return false;
        }
    }

    // ===== 实体类映射 =====

    @Override
    public DatasourceEntityMapping getMappingByClazz(String clazz) {
        return entityMappingMapper.selectOne(
                new LambdaQueryWrapper<DatasourceEntityMapping>()
                        .eq(DatasourceEntityMapping::getClazz, clazz));
    }

    @Override
    public List<DatasourceEntityMapping> listEntityMappings() {
        return entityMappingMapper.selectList(null);
    }

    @Override
    public void saveEntityMapping(String clazz, Long datasourceId) {
        DatasourceEntityMapping existing = getMappingByClazz(clazz);
        if (existing != null) {
            existing.setDatasourceId(datasourceId);
            entityMappingMapper.updateById(existing);
        } else {
            DatasourceEntityMapping mapping = new DatasourceEntityMapping();
            mapping.setClazz(clazz);
            mapping.setDatasourceId(datasourceId);
            entityMappingMapper.insert(mapping);
        }
    }

    @Override
    public void deleteEntityMapping(String clazz) {
        entityMappingMapper.delete(
                new LambdaQueryWrapper<DatasourceEntityMapping>()
                        .eq(DatasourceEntityMapping::getClazz, clazz));
    }

    // ===== 字段映射 =====

    @Override
    public List<DatasourceFieldMapping> getFieldMappings(Long datasourceId, String clazz) {
        return fieldMappingMapper.selectList(
                new LambdaQueryWrapper<DatasourceFieldMapping>()
                        .eq(DatasourceFieldMapping::getDatasourceId, datasourceId)
                        .eq(DatasourceFieldMapping::getClazz, clazz));
    }

    @Override
    public void saveFieldMappings(Long datasourceId, String clazz, List<DatasourceFieldMapping> mappings) {
        // 先删除旧映射
        fieldMappingMapper.delete(
                new LambdaQueryWrapper<DatasourceFieldMapping>()
                        .eq(DatasourceFieldMapping::getDatasourceId, datasourceId)
                        .eq(DatasourceFieldMapping::getClazz, clazz));
        // 批量插入新映射
        for (DatasourceFieldMapping mapping : mappings) {
            mapping.setId(null);
            mapping.setDatasourceId(datasourceId);
            mapping.setClazz(clazz);
            fieldMappingMapper.insert(mapping);
        }
        // 清除缓存
        evictFieldMappingCache(datasourceId);
    }

    // ===== 路由查询 =====

    @Override
    public Datasource resolveDatasource(String clazz) {
        DatasourceEntityMapping mapping = getMappingByClazz(clazz);
        if (mapping == null) {
            return null;
        }
        return datasourceMapper.selectById(mapping.getDatasourceId());
    }

    @Override
    public String resolveRemoteField(Long datasourceId, String clazz, String variableName) {
        Map<String, String> cache = getFieldMappingCache(datasourceId, clazz);
        return cache.get(variableName);
    }

    @Override
    public Map<String, String> getFieldMappingCache(Long datasourceId, String clazz) {
        String cacheKey = datasourceId + ":" + clazz;
        return fieldMappingCache.computeIfAbsent(cacheKey, k -> {
            List<DatasourceFieldMapping> mappings = getFieldMappings(datasourceId, clazz);
            if (mappings == null || mappings.isEmpty()) {
                return Collections.emptyMap();
            }
            return mappings.stream()
                    .collect(Collectors.toMap(
                            DatasourceFieldMapping::getVariableName,
                            DatasourceFieldMapping::getRemoteField,
                            (a, b) -> b));
        });
    }

    // ===== 内部方法 =====

    private DataSourceConnector resolveConnector(String type) {
        if (connectors == null) {
            return null;
        }
        return connectors.stream()
                .filter(c -> c.getConnectorType().equals(type))
                .findFirst()
                .orElse(null);
    }

    private void evictFieldMappingCache(Long datasourceId) {
        // 清除该 datasourceId 相关的所有缓存
        fieldMappingCache.keySet().removeIf(key -> key.startsWith(datasourceId + ":"));
    }
}
