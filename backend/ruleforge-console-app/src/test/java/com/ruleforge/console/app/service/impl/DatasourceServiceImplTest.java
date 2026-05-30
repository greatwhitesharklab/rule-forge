package com.ruleforge.console.app.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.console.app.entity.Datasource;
import com.ruleforge.console.app.entity.DatasourceEntityMapping;
import com.ruleforge.console.app.entity.DatasourceFieldMapping;
import com.ruleforge.console.app.mapper.DatasourceEntityMappingMapper;
import com.ruleforge.console.app.mapper.DatasourceFieldMappingMapper;
import com.ruleforge.console.app.mapper.DatasourceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 数据源管理 CRUD
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatasourceServiceImpl - 数据源管理")
class DatasourceServiceImplTest {

    @Mock private DatasourceMapper datasourceMapper;
    @Mock private DatasourceEntityMappingMapper entityMappingMapper;
    @Mock private DatasourceFieldMappingMapper fieldMappingMapper;

    @InjectMocks
    private DatasourceServiceImpl service;

    private Datasource buildDatasource(Long id, String name, String type) {
        Datasource ds = new Datasource();
        ds.setId(id);
        ds.setName(name);
        ds.setType(type);
        ds.setConfigJson("{}");
        ds.setEnabled(true);
        ds.setTimeoutMs(30000);
        ds.setCacheEnabled(true);
        ds.setCacheTtlHours(120);
        return ds;
    }

    @Nested
    @DisplayName("Scenario: 数据源 CRUD")
    class DatasourceCrud {

        @Test
        @DisplayName("创建数据源")
        void shouldCreateDatasource() {
            // Given
            Datasource ds = buildDatasource(null, "test-ds", "REST_API");
            when(datasourceMapper.insert(any(Datasource.class))).thenAnswer(inv -> {
                inv.getArgument(0, Datasource.class).setId(1L);
                return 1;
            });

            // When
            Datasource result = service.createDatasource(ds);

            // Then
            assertThat(result.getId()).isEqualTo(1L);
            verify(datasourceMapper).insert(any(Datasource.class));
        }

        @Test
        @DisplayName("更新数据源并清除缓存")
        void shouldUpdateDatasourceAndEvictCache() {
            // Given — 先填充字段映射缓存
            DatasourceFieldMapping fm = new DatasourceFieldMapping();
            fm.setVariableName("score");
            fm.setRemoteField("credit_score");
            when(fieldMappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(fm));
            service.getFieldMappingCache(1L, "com.test.Model");
            assertThat(service.resolveRemoteField(1L, "com.test.Model", "score")).isEqualTo("credit_score");

            Datasource ds = buildDatasource(1L, "test-ds", "REST_API");

            // When
            service.updateDatasource(ds);

            // Then — 缓存被清除，再次查 DB 返回空
            reset(fieldMappingMapper);
            when(fieldMappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            verify(datasourceMapper).updateById(ds);
            assertThat(service.resolveRemoteField(1L, "com.test.Model", "score")).isNull();
        }

        @Test
        @DisplayName("删除数据源")
        void shouldDeleteDatasource() {
            // When
            service.deleteDatasource(1L);

            // Then
            verify(datasourceMapper).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("Scenario: 实体类映射")
    class EntityMapping {

        @Test
        @DisplayName("创建新的实体类映射")
        void shouldCreateNewEntityMapping() {
            // Given
            when(entityMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(entityMappingMapper.insert(any(DatasourceEntityMapping.class))).thenReturn(1);

            // When
            service.saveEntityMapping("com.test.Model", 1L);

            // Then
            ArgumentCaptor<DatasourceEntityMapping> captor = ArgumentCaptor.forClass(DatasourceEntityMapping.class);
            verify(entityMappingMapper).insert(captor.capture());
            assertThat(captor.getValue().getClazz()).isEqualTo("com.test.Model");
            assertThat(captor.getValue().getDatasourceId()).isEqualTo(1L);
            verify(entityMappingMapper, never()).updateById(any(DatasourceEntityMapping.class));
        }

        @Test
        @DisplayName("更新已有实体类映射")
        void shouldUpdateExistingEntityMapping() {
            // Given
            DatasourceEntityMapping existing = new DatasourceEntityMapping();
            existing.setId(10L);
            existing.setClazz("com.test.Model");
            existing.setDatasourceId(1L);
            when(entityMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

            // When
            service.saveEntityMapping("com.test.Model", 2L);

            // Then
            ArgumentCaptor<DatasourceEntityMapping> captor = ArgumentCaptor.forClass(DatasourceEntityMapping.class);
            verify(entityMappingMapper).updateById(captor.capture());
            assertThat(captor.getValue().getDatasourceId()).isEqualTo(2L);
            verify(entityMappingMapper, never()).insert(any(DatasourceEntityMapping.class));
        }
    }

    @Nested
    @DisplayName("Scenario: 字段映射")
    class FieldMapping {

        @Test
        @DisplayName("保存字段映射（先删后插）")
        void shouldSaveFieldMappings() {
            // Given
            when(fieldMappingMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);
            when(fieldMappingMapper.insert(any(DatasourceFieldMapping.class))).thenReturn(1);

            DatasourceFieldMapping fm1 = new DatasourceFieldMapping();
            fm1.setVariableName("score");
            fm1.setRemoteField("credit_score");
            DatasourceFieldMapping fm2 = new DatasourceFieldMapping();
            fm2.setVariableName("age");
            fm2.setRemoteField("user_age");

            // When
            service.saveFieldMappings(1L, "com.test.Model", List.of(fm1, fm2));

            // Then — 先删后插 2 条
            verify(fieldMappingMapper).delete(any(LambdaQueryWrapper.class));
            verify(fieldMappingMapper, times(2)).insert(any(DatasourceFieldMapping.class));
        }

        @Test
        @DisplayName("解析字段映射返回 remoteField")
        void shouldResolveRemoteField() {
            // Given
            DatasourceFieldMapping fm = new DatasourceFieldMapping();
            fm.setVariableName("score");
            fm.setRemoteField("credit_score");
            when(fieldMappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(fm));

            // When
            String result = service.resolveRemoteField(1L, "com.test.Model", "score");

            // Then
            assertThat(result).isEqualTo("credit_score");
        }

        @Test
        @DisplayName("无映射时返回 null")
        void shouldReturnNullWhenNoMapping() {
            // Given
            when(fieldMappingMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            // When
            String result = service.resolveRemoteField(1L, "com.test.Model", "unknown_field");

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("Scenario: 数据源路由解析")
    class DatasourceRouting {

        @Test
        @DisplayName("通过 clazz 解析到对应数据源")
        void shouldResolveDatasourceByClazz() {
            // Given
            DatasourceEntityMapping mapping = new DatasourceEntityMapping();
            mapping.setClazz("com.test.Model");
            mapping.setDatasourceId(1L);
            when(entityMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mapping);

            Datasource ds = buildDatasource(1L, "test-ds", "ADVANCE_AI");
            when(datasourceMapper.selectById(1L)).thenReturn(ds);

            // When
            Datasource result = service.resolveDatasource("com.test.Model");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getType()).isEqualTo("ADVANCE_AI");
        }

        @Test
        @DisplayName("无映射时返回 null")
        void shouldReturnNullWhenNoMapping() {
            // Given
            when(entityMappingMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // When
            Datasource result = service.resolveDatasource("com.test.UnknownModel");

            // Then
            assertThat(result).isNull();
            verify(datasourceMapper, never()).selectById(anyLong());
        }
    }
}
