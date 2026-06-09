package com.ruleforge.decision.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.decision.entity.Datasource;
import com.ruleforge.decision.entity.DatasourceEntityMapping;
import com.ruleforge.decision.entity.DatasourceFieldMapping;
import com.ruleforge.decision.entity.DatasourceLog;
import com.ruleforge.decision.entity.RuleVariableDef;
import com.ruleforge.decision.mapper.DatasourceEntityMappingMapper;
import com.ruleforge.decision.mapper.DatasourceFieldMappingMapper;
import com.ruleforge.decision.mapper.DatasourceLogMapper;
import com.ruleforge.decision.mapper.DatasourceMapper;
import com.ruleforge.decision.mapper.RuleVariableDefMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceRepositoryImpl implements DatasourceRepository {

    private final RuleVariableDefMapper ruleVariableDefMapper;
    private final DatasourceEntityMappingMapper entityMappingMapper;
    private final DatasourceFieldMappingMapper fieldMappingMapper;
    private final DatasourceLogMapper datasourceLogMapper;
    private final DatasourceMapper datasourceMapper;

    // ===== RuleVariableDef =====

    @Override
    public List<RuleVariableDef> findVariableDefsByClazz(String clazz) {
        return ruleVariableDefMapper.selectList(
                new LambdaQueryWrapper<RuleVariableDef>()
                        .eq(RuleVariableDef::getClazz, clazz)
                        .eq(RuleVariableDef::getDsStatus, 1)
                        .orderByAsc(RuleVariableDef::getSortNo)
        );
    }

    @Override
    public void insertVariableDefs(List<RuleVariableDef> entities) {
        for (RuleVariableDef entity : entities) {
            ruleVariableDefMapper.insert(entity);
        }
    }

    @Override
    public void deleteVariableDefsByClazz(String clazz) {
        ruleVariableDefMapper.delete(
                new LambdaQueryWrapper<RuleVariableDef>()
                        .eq(RuleVariableDef::getClazz, clazz)
        );
    }

    @Override
    public List<RuleVariableDef> findAllVariableDefs() {
        return ruleVariableDefMapper.selectList(null);
    }

    // ===== DatasourceEntityMapping =====

    @Override
    public DatasourceEntityMapping findEntityMappingByClazz(String clazz) {
        return entityMappingMapper.selectOne(
                new LambdaQueryWrapper<DatasourceEntityMapping>()
                        .eq(DatasourceEntityMapping::getClazz, clazz)
        );
    }

    @Override
    public void insertEntityMapping(DatasourceEntityMapping entity) {
        entityMappingMapper.insert(entity);
    }

    @Override
    public void updateEntityMapping(DatasourceEntityMapping entity) {
        entityMappingMapper.updateById(entity);
    }

    @Override
    public void deleteEntityMappingByClazz(String clazz) {
        entityMappingMapper.delete(
                new LambdaQueryWrapper<DatasourceEntityMapping>()
                        .eq(DatasourceEntityMapping::getClazz, clazz)
        );
    }

    @Override
    public List<DatasourceEntityMapping> findAllEntityMappings() {
        return entityMappingMapper.selectList(null);
    }

    // ===== DatasourceFieldMapping =====

    @Override
    public List<DatasourceFieldMapping> findFieldMappings(Long datasourceId, String clazz) {
        return fieldMappingMapper.selectList(
                new LambdaQueryWrapper<DatasourceFieldMapping>()
                        .eq(DatasourceFieldMapping::getDatasourceId, datasourceId)
                        .eq(DatasourceFieldMapping::getClazz, clazz)
        );
    }

    @Override
    public void insertFieldMappings(List<DatasourceFieldMapping> entities) {
        for (DatasourceFieldMapping entity : entities) {
            fieldMappingMapper.insert(entity);
        }
    }

    @Override
    public void deleteFieldMappings(Long datasourceId, String clazz) {
        fieldMappingMapper.delete(
                new LambdaQueryWrapper<DatasourceFieldMapping>()
                        .eq(DatasourceFieldMapping::getDatasourceId, datasourceId)
                        .eq(DatasourceFieldMapping::getClazz, clazz)
        );
    }

    // ===== DatasourceLog =====

    @Override
    public DatasourceLog findCachedLog(Long datasourceId, String userId, String apiEndpoint) {
        return datasourceLogMapper.selectOne(
                new LambdaQueryWrapper<DatasourceLog>()
                        .eq(DatasourceLog::getDatasourceId, datasourceId)
                        .eq(DatasourceLog::getUserId, userId)
                        .eq(DatasourceLog::getApiEndpoint, apiEndpoint)
                        .eq(DatasourceLog::getStatus, "SUCCESS")
                        .isNotNull(DatasourceLog::getResponseData)
                        .orderByDesc(DatasourceLog::getCreatedAt)
                        .last("LIMIT 1")
        );
    }

    @Override
    public void insertDatasourceLog(DatasourceLog entity) {
        datasourceLogMapper.insert(entity);
    }

    // ===== Datasource =====

    @Override
    public Datasource findDatasourceById(Long id) {
        return datasourceMapper.selectById(id);
    }

    @Override
    public List<Datasource> findAllDatasources() {
        return datasourceMapper.selectList(null);
    }

    @Override
    public Datasource insertDatasource(Datasource entity) {
        datasourceMapper.insert(entity);
        return entity;
    }

    @Override
    public void updateDatasource(Datasource entity) {
        datasourceMapper.updateById(entity);
    }

    @Override
    public void deleteDatasource(Long id) {
        datasourceMapper.deleteById(id);
    }
}
