package com.ruleforge.decision.service.impl;

import com.ruleforge.decision.connector.DataSourceConnector;
import com.ruleforge.decision.entity.Datasource;
import com.ruleforge.decision.entity.DatasourceEntityMapping;
import com.ruleforge.decision.entity.DatasourceFieldMapping;
import com.ruleforge.decision.repository.DatasourceRepository;
import com.ruleforge.decision.service.IDatasourceService;
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

    private final DatasourceRepository datasourceRepository;
    private final List<DataSourceConnector> connectors;

    // ===== 字段映射内存缓存 =====
    private final ConcurrentHashMap<String, Map<String, String>> fieldMappingCache = new ConcurrentHashMap<>();

    // ===== 数据源 CRUD =====

    @Override
    public List<Datasource> listDatasources() {
        return datasourceRepository.findAllDatasources();
    }

    @Override
    public Datasource getDatasourceById(Long id) {
        return datasourceRepository.findDatasourceById(id);
    }

    @Override
    public Datasource createDatasource(Datasource datasource) {
        datasource.setCreatedAt(null);
        datasource.setUpdatedAt(null);
        datasourceRepository.insertDatasource(datasource);
        return datasource;
    }

    @Override
    public Datasource updateDatasource(Datasource datasource) {
        datasourceRepository.updateDatasource(datasource);
        // 清除字段映射缓存
        evictFieldMappingCache(datasource.getId());
        return datasource;
    }

    @Override
    public void deleteDatasource(Long id) {
        datasourceRepository.deleteDatasource(id);
        evictFieldMappingCache(id);
    }

    @Override
    public boolean testConnection(Long id) {
        Datasource ds = datasourceRepository.findDatasourceById(id);
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
        return datasourceRepository.findEntityMappingByClazz(clazz);
    }

    @Override
    public List<DatasourceEntityMapping> listEntityMappings() {
        return datasourceRepository.findAllEntityMappings();
    }

    @Override
    public void saveEntityMapping(String clazz, Long datasourceId) {
        DatasourceEntityMapping existing = getMappingByClazz(clazz);
        if (existing != null) {
            existing.setDatasourceId(datasourceId);
            datasourceRepository.updateEntityMapping(existing);
        } else {
            DatasourceEntityMapping mapping = new DatasourceEntityMapping();
            mapping.setClazz(clazz);
            mapping.setDatasourceId(datasourceId);
            datasourceRepository.insertEntityMapping(mapping);
        }
    }

    @Override
    public void deleteEntityMapping(String clazz) {
        datasourceRepository.deleteEntityMappingByClazz(clazz);
    }

    // ===== 字段映射 =====

    @Override
    public List<DatasourceFieldMapping> getFieldMappings(Long datasourceId, String clazz) {
        return datasourceRepository.findFieldMappings(datasourceId, clazz);
    }

    @Override
    public void saveFieldMappings(Long datasourceId, String clazz, List<DatasourceFieldMapping> mappings) {
        // 先删除旧映射
        datasourceRepository.deleteFieldMappings(datasourceId, clazz);
        // 批量插入新映射
        for (DatasourceFieldMapping mapping : mappings) {
            mapping.setId(null);
            mapping.setDatasourceId(datasourceId);
            mapping.setClazz(clazz);
        }
        datasourceRepository.insertFieldMappings(mappings);
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
        return datasourceRepository.findDatasourceById(mapping.getDatasourceId());
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
