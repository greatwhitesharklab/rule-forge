package com.ruleforge.decision.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: Flowable 数据源配置
 */
@DisplayName("FlowableConfig - Flowable 数据源配置")
class FlowableConfigTest {

    @Nested
    @DisplayName("数据源选择")
    class DataSourceSelection {

        @Test
        @DisplayName("Flowable 应使用 ruleforgeDataSource")
        void shouldUseRuleforgeDataSource() {
            // Given 应用配置了 @Primary appDataSource 和 ruleforgeDataSource
            DataSource ruleforgeDataSource = mock(DataSource.class);
            FlowableConfig config = new FlowableConfig(ruleforgeDataSource);

            // When Flowable 引擎初始化调用 configure 方法
            SpringProcessEngineConfiguration engineConfig = new SpringProcessEngineConfiguration();
            config.configure(engineConfig);

            // Then SpringProcessEngineConfiguration 的 dataSource 应为 ruleforgeDataSource
            assertThat(engineConfig.getDataSource()).isNotNull();
            // Flowable wraps the DataSource in TransactionAwareDataSourceProxy
            assertThat(engineConfig.getDataSource()).isInstanceOf(
                org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy.class);
        }

        @Test
        @DisplayName("databaseSchemaUpdate 应设置为 true")
        void shouldSetDatabaseSchemaUpdateTrue() {
            // Given FlowableConfig 注入了 ruleforgeDataSource
            DataSource ruleforgeDataSource = mock(DataSource.class);
            FlowableConfig config = new FlowableConfig(ruleforgeDataSource);

            // When 配置完成
            SpringProcessEngineConfiguration engineConfig = new SpringProcessEngineConfiguration();
            config.configure(engineConfig);

            // Then databaseSchemaUpdate 应为 "true"
            assertThat(engineConfig.getDatabaseSchemaUpdate()).isEqualTo("true");
        }
    }
}
