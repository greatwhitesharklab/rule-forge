package com.ruleforge.console.app.lazy;

import com.ruleforge.console.app.connector.DataSourceConnector;
import com.ruleforge.console.app.entity.Datasource;
import com.ruleforge.console.app.service.IDatasourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 数据源路由 Provider
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatasourceRoutingProvider - 数据源路由")
class DatasourceRoutingProviderTest {

    @Mock private IDatasourceService datasourceService;
    @Mock private DataSourceConnector advanceAiConnector;

    @InjectMocks
    private DatasourceRoutingProvider provider;

    private Datasource buildDatasource(Long id, String type) {
        Datasource ds = new Datasource();
        ds.setId(id);
        ds.setType(type);
        ds.setConfigJson("{}");
        ds.setEnabled(true);
        ds.setCacheEnabled(false);
        return ds;
    }

    @BeforeEach
    void setUp() {
        // 注入 connectors 列表
        // 由于 @InjectMocks 不会注入 List，需要手动设置
        // 通过反射或构造器
    }

    private DatasourceRoutingProvider createProvider(DataSourceConnector... connectors) {
        return new DatasourceRoutingProvider(datasourceService, List.of(connectors));
    }

    @Nested
    @DisplayName("Scenario: clazz 路由到正确 connector")
    class ClazzRouting {

        @Test
        @DisplayName("clazz 有映射时委托到对应 connector")
        void shouldRouteToCorrectConnector() {
            // Given
            Datasource ds = buildDatasource(1L, "ADVANCE_AI");
            when(datasourceService.resolveDatasource("com.test.Model")).thenReturn(ds);
            when(advanceAiConnector.getConnectorType()).thenReturn("ADVANCE_AI");
            when(advanceAiConnector.fetchFieldValue(eq(ds), eq("user1"), eq("com.test.Model"),
                    eq("advance_gd_x_19"), anyMap())).thenReturn(0.5);

            DatasourceRoutingProvider p = createProvider(advanceAiConnector);

            // When
            Object value = p.fetchFieldValue("user1", "com.test.Model", "advance_gd_x_19");

            // Then
            assertThat(value).isEqualTo(0.5);
            verify(advanceAiConnector).fetchFieldValue(eq(ds), eq("user1"), eq("com.test.Model"),
                    eq("advance_gd_x_19"), anyMap());
        }

        @Test
        @DisplayName("无 clazz 映射时返回 -999")
        void shouldReturnFallbackWhenNoMapping() {
            // Given
            when(datasourceService.resolveDatasource("com.test.UnknownModel")).thenReturn(null);

            DatasourceRoutingProvider p = createProvider(advanceAiConnector);

            // When
            Object value = p.fetchFieldValue("user1", "com.test.UnknownModel", "some_field");

            // Then
            assertThat(value).isEqualTo(-999);
            verify(advanceAiConnector, never()).fetchFieldValue(any(), anyString(), anyString(), anyString(), anyMap());
        }
    }

    @Nested
    @DisplayName("Scenario: 字段名映射")
    class FieldNameMapping {

        @Test
        @DisplayName("有映射时传递 remoteField 给 connector")
        void shouldPassRemoteFieldToConnector() {
            // Given
            Datasource ds = buildDatasource(1L, "ADVANCE_AI");
            when(datasourceService.resolveDatasource("com.test.Model")).thenReturn(ds);
            when(datasourceService.resolveRemoteField(1L, "com.test.Model", "score")).thenReturn("credit_score");
            when(advanceAiConnector.getConnectorType()).thenReturn("ADVANCE_AI");
            when(advanceAiConnector.fetchFieldValue(eq(ds), eq("user1"), eq("com.test.Model"),
                    eq("credit_score"), anyMap())).thenReturn(677.0);

            DatasourceRoutingProvider p = createProvider(advanceAiConnector);

            // When
            Object value = p.fetchFieldValue("user1", "com.test.Model", "score");

            // Then — "score" 被映射为 "credit_score"
            assertThat(value).isEqualTo(677.0);
            verify(advanceAiConnector).fetchFieldValue(eq(ds), eq("user1"), eq("com.test.Model"),
                    eq("credit_score"), anyMap());
        }

        @Test
        @DisplayName("无映射时原样传递 fieldName")
        void shouldPassOriginalFieldWhenNoMapping() {
            // Given
            Datasource ds = buildDatasource(1L, "ADVANCE_AI");
            when(datasourceService.resolveDatasource("com.test.Model")).thenReturn(ds);
            when(datasourceService.resolveRemoteField(1L, "com.test.Model", "direct_field")).thenReturn(null);
            when(advanceAiConnector.getConnectorType()).thenReturn("ADVANCE_AI");
            when(advanceAiConnector.fetchFieldValue(eq(ds), eq("user1"), eq("com.test.Model"),
                    eq("direct_field"), anyMap())).thenReturn(42);

            DatasourceRoutingProvider p = createProvider(advanceAiConnector);

            // When
            Object value = p.fetchFieldValue("user1", "com.test.Model", "direct_field");

            // Then
            assertThat(value).isEqualTo(42);
            verify(advanceAiConnector).fetchFieldValue(eq(ds), eq("user1"), eq("com.test.Model"),
                    eq("direct_field"), anyMap());
        }
    }

    @Nested
    @DisplayName("Scenario: connector 异常")
    class ConnectorException {

        @Test
        @DisplayName("connector 抛异常时返回 -999")
        void shouldReturnFallbackOnException() {
            // Given
            Datasource ds = buildDatasource(1L, "ADVANCE_AI");
            when(datasourceService.resolveDatasource("com.test.Model")).thenReturn(ds);
            when(advanceAiConnector.getConnectorType()).thenReturn("ADVANCE_AI");
            when(advanceAiConnector.fetchFieldValue(any(), anyString(), anyString(), anyString(), anyMap()))
                    .thenThrow(new RuntimeException("API down"));

            DatasourceRoutingProvider p = createProvider(advanceAiConnector);

            // When
            Object value = p.fetchFieldValue("user1", "com.test.Model", "some_field");

            // Then
            assertThat(value).isEqualTo(-999);
        }
    }

    @Nested
    @DisplayName("Scenario: 路由缓存")
    class RouteCache {

        @Test
        @DisplayName("连续调用同一 clazz 只查一次数据库")
        void shouldCacheRouteResolution() {
            // Given
            Datasource ds = buildDatasource(1L, "ADVANCE_AI");
            when(datasourceService.resolveDatasource("com.test.Model")).thenReturn(ds);
            when(advanceAiConnector.getConnectorType()).thenReturn("ADVANCE_AI");
            when(advanceAiConnector.fetchFieldValue(any(), anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(1);

            DatasourceRoutingProvider p = createProvider(advanceAiConnector);

            // When — 连续调用两次
            p.fetchFieldValue("user1", "com.test.Model", "field1");
            p.fetchFieldValue("user2", "com.test.Model", "field2");

            // Then — resolveDatasource 只调一次
            verify(datasourceService, times(1)).resolveDatasource("com.test.Model");
        }

        @Test
        @DisplayName("evictRouteCache 后重新查数据库")
        void shouldRequeryAfterEvict() {
            // Given
            Datasource ds = buildDatasource(1L, "ADVANCE_AI");
            when(datasourceService.resolveDatasource("com.test.Model")).thenReturn(ds);
            when(advanceAiConnector.getConnectorType()).thenReturn("ADVANCE_AI");
            when(advanceAiConnector.fetchFieldValue(any(), anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(1);

            DatasourceRoutingProvider p = createProvider(advanceAiConnector);
            p.fetchFieldValue("user1", "com.test.Model", "field1");

            // When — 清除缓存
            p.evictRouteCache();
            p.fetchFieldValue("user2", "com.test.Model", "field2");

            // Then — resolveDatasource 被调两次
            verify(datasourceService, times(2)).resolveDatasource("com.test.Model");
        }
    }
}
