package com.ruleforge.console.app.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.Datasource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Feature: JDBC 数据源连接器
 *
 * 注意：实际 JDBC 查询需要真实数据库，此处测试连接池管理和连接器类型。
 * 查询逻辑通过集成测试验证。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JdbcDataSourceConnector - JDBC 数据源连接器")
class JdbcDataSourceConnectorTest {

    private JdbcDataSourceConnector connector;

    @AfterEach
    void tearDown() {
        if (connector != null) {
            connector.cleanup();
        }
    }

    @Nested
    @DisplayName("Scenario: 连接器类型")
    class ConnectorType {

        @Test
        @DisplayName("返回 JDBC 类型")
        void shouldReturnJdbcType() {
            connector = new JdbcDataSourceConnector();
            assertThat(connector.getConnectorType()).isEqualTo("JDBC");
        }
    }

    @Nested
    @DisplayName("Scenario: 无效数据源配置")
    class InvalidConfig {

        @Test
        @DisplayName("无效 URL 时 fetchFieldValue 返回 null")
        void shouldReturnNullOnInvalidUrl() {
            connector = new JdbcDataSourceConnector();
            Datasource ds = new Datasource();
            ds.setId(99L);
            ds.setConfigJson("{\"url\":\"jdbc:invalid://nonexistent\",\"username\":\"u\",\"password\":\"p\"," +
                    "\"driverClass\":\"com.mysql.cj.jdbc.Driver\",\"queryTemplate\":\"SELECT ${fieldName} FROM t WHERE id='${entityId}'\"}");

            // When
            Object value = connector.fetchFieldValue(ds, "user1", "com.test.Model", "score", Map.of());

            // Then — 连接失败，返回 null
            assertThat(value).isNull();
        }

        @Test
        @DisplayName("无效 URL 时 testConnection 返回 false")
        void shouldReturnFalseOnInvalidUrl() {
            connector = new JdbcDataSourceConnector();
            Datasource ds = new Datasource();
            ds.setId(99L);
            ds.setConfigJson("{\"url\":\"jdbc:invalid://nonexistent\",\"username\":\"u\",\"password\":\"p\"," +
                    "\"driverClass\":\"com.mysql.cj.jdbc.Driver\",\"queryTemplate\":\"SELECT 1\"}");

            // When
            boolean result = connector.testConnection(ds);

            // Then
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario: 连接池管理")
    class PoolManagement {

        @Test
        @DisplayName("cleanup 不抛异常")
        void shouldCleanupWithoutError() {
            connector = new JdbcDataSourceConnector();
            assertThatCode(() -> connector.cleanup()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("evictPool 不存在的 datasourceId 不抛异常")
        void shouldEvictNonExistentPool() {
            connector = new JdbcDataSourceConnector();
            assertThatCode(() -> connector.evictPool(99999L)).doesNotThrowAnyException();
        }
    }
}
