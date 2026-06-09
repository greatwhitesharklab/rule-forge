package com.ruleforge.decision.repository;

import com.ruleforge.decision.entity.Datasource;
import com.ruleforge.decision.entity.DatasourceEntityMapping;
import com.ruleforge.decision.entity.DatasourceFieldMapping;
import com.ruleforge.decision.entity.DatasourceLog;
import com.ruleforge.decision.entity.RuleVariableDef;

import java.util.List;

/**
 * Datasource data access repository.
 * Encapsulates all DB operations for Datasource, entity/field mappings,
 * DatasourceLog, and RuleVariableDef.
 */
public interface DatasourceRepository {

    // ===== RuleVariableDef =====

    /**
     * Find variable definitions by clazz where dsStatus=1, ordered by sortNo asc.
     */
    List<RuleVariableDef> findVariableDefsByClazz(String clazz);

    /**
     * Batch insert variable definitions.
     */
    void insertVariableDefs(List<RuleVariableDef> entities);

    /**
     * Delete all variable definitions for a given clazz.
     */
    void deleteVariableDefsByClazz(String clazz);

    /**
     * Find all variable definitions.
     */
    List<RuleVariableDef> findAllVariableDefs();

    // ===== DatasourceEntityMapping =====

    /**
     * Find entity mapping by clazz.
     */
    DatasourceEntityMapping findEntityMappingByClazz(String clazz);

    /**
     * Insert an entity mapping.
     */
    void insertEntityMapping(DatasourceEntityMapping entity);

    /**
     * Update an existing entity mapping.
     */
    void updateEntityMapping(DatasourceEntityMapping entity);

    /**
     * Delete entity mapping by clazz.
     */
    void deleteEntityMappingByClazz(String clazz);

    /**
     * Find all entity mappings.
     */
    List<DatasourceEntityMapping> findAllEntityMappings();

    // ===== DatasourceFieldMapping =====

    /**
     * Find field mappings by datasourceId and clazz.
     */
    List<DatasourceFieldMapping> findFieldMappings(Long datasourceId, String clazz);

    /**
     * Batch insert field mappings.
     */
    void insertFieldMappings(List<DatasourceFieldMapping> entities);

    /**
     * Delete field mappings by datasourceId and clazz.
     */
    void deleteFieldMappings(Long datasourceId, String clazz);

    // ===== DatasourceLog =====

    /**
     * Find the most recent successful cached log entry for the given datasourceId,
     * userId, and apiEndpoint, where responseData is not null.
     * Ordered by createdAt desc, limited to 1 result.
     */
    DatasourceLog findCachedLog(Long datasourceId, String userId, String apiEndpoint);

    /**
     * Insert a datasource log entry.
     */
    void insertDatasourceLog(DatasourceLog entity);

    // ===== Datasource =====

    /**
     * Find a datasource by id.
     */
    Datasource findDatasourceById(Long id);

    /**
     * Find all datasources.
     */
    List<Datasource> findAllDatasources();

    /**
     * Insert a datasource.
     */
    Datasource insertDatasource(Datasource entity);

    /**
     * Update a datasource.
     */
    void updateDatasource(Datasource entity);

    /**
     * Delete a datasource by id.
     */
    void deleteDatasource(Long id);
}
