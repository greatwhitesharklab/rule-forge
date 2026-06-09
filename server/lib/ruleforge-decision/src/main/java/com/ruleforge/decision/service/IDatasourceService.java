package com.ruleforge.decision.service;

import com.ruleforge.decision.entity.Datasource;
import com.ruleforge.decision.entity.DatasourceEntityMapping;
import com.ruleforge.decision.entity.DatasourceFieldMapping;

import java.util.List;
import java.util.Map;

/**
 * 数据源管理服务
 */
public interface IDatasourceService {

    // ===== 数据源 CRUD =====

    List<Datasource> listDatasources();

    Datasource getDatasourceById(Long id);

    Datasource createDatasource(Datasource datasource);

    Datasource updateDatasource(Datasource datasource);

    void deleteDatasource(Long id);

    boolean testConnection(Long id);

    // ===== 实体类映射 =====

    DatasourceEntityMapping getMappingByClazz(String clazz);

    List<DatasourceEntityMapping> listEntityMappings();

    void saveEntityMapping(String clazz, Long datasourceId);

    void deleteEntityMapping(String clazz);

    // ===== 字段映射 =====

    List<DatasourceFieldMapping> getFieldMappings(Long datasourceId, String clazz);

    void saveFieldMappings(Long datasourceId, String clazz, List<DatasourceFieldMapping> mappings);

    // ===== 字段值查询（供 DatasourceRoutingProvider 使用） =====

    /**
     * 解析 clazz 对应的数据源和字段映射
     * @return datasource 实体，如果没有映射返回 null
     */
    Datasource resolveDatasource(String clazz);

    /**
     * 查询字段映射（variableName → remoteField）
     * @return 映射的 remoteField，如果没有映射返回 null
     */
    String resolveRemoteField(Long datasourceId, String clazz, String variableName);

    /**
     * 获取某个 clazz 对应的所有字段映射
     */
    Map<String, String> getFieldMappingCache(Long datasourceId, String clazz);
}
