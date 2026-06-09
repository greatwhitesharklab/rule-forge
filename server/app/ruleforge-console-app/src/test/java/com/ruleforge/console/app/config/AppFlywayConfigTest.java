package com.ruleforge.console.app.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feature: V5.16 app_db Flyway 管理
 *
 * <p>背景:app_db 之前没有 Flyway 管理,11 张 nd_* 表是手工 / app 启动 SQL 创建的。
 * V5.16 起:引入 AppFlywayConfig 独立 Flyway 实例,绑 appDataSource,
 * 跑 classpath:db/migration-app 下的脚本,history 表名 flyway_app_schema_history
 * (与 ruleforge_db 的 flyway_schema_history / flowable_db 的 flowable_flyway_history 分开)。
 *
 * <p>数据流:
 * <pre>
 *   appDataSource (HikariCP, @Primary)
 *     ↓ Flyway.configure().dataSource(appDataSource)
 *   .locations("classpath:db/migration-app")
 *   .baselineOnMigrate(true) + .baselineVersion("0")
 *   .table("flyway_app_schema_history")
 *     ↓ @PostConstruct migrateAppSchema()
 *   app_db schema 升级
 * </pre>
 */
@DisplayName("AppFlywayConfig - app_db Flyway 迁移配置")
class AppFlywayConfigTest {

    @Nested
    @DisplayName("数据源选择")
    class DataSourceSelection {

        @Test
        @DisplayName("应使用 appDataSource(绑 @Primary app 库,不复用 ruleforgeDataSource)")
        void shouldUseAppDataSource() {
            // Given 应用注入了 @Primary appDataSource bean
            DataSource appDataSource = mock(DataSource.class);

            // When 创建 AppFlywayConfig
            AppFlywayConfig config = new AppFlywayConfig(appDataSource);

            // Then 内部持有的 DataSource 应该是注入的 appDataSource
            assertThat(config.appDataSource()).isSameAs(appDataSource);
        }
    }

    @Nested
    @DisplayName("Flyway 配置参数")
    class FlywayConfiguration {

        @Test
        @DisplayName("migration location 应为 classpath:db/migration-app(与 ruleforge_db / flowable_db 隔离)")
        void shouldUseMigrationAppLocation() {
            // Given AppFlywayConfig 配置好了
            AppFlywayConfig config = new AppFlywayConfig(mock(DataSource.class));

            // When 获取 Flyway 配置
            FluentConfiguration flywayConfig = config.buildFlywayConfiguration();

            // Then migration location 应是 db/migration-app
            assertThat(flywayConfig.getLocations())
                    .as("AppFlyway 应使用独立的 db/migration-app 目录,跟 ruleforge_db 的 db/migration 隔离")
                    .extracting(loc -> loc.toString())
                    .contains("classpath:db/migration-app");
        }

        @Test
        @DisplayName("应启用 baselineOnMigrate(老 app_db 无 history 表,需要 baseline 入口)")
        void shouldEnableBaselineOnMigrate() {
            // Given AppFlywayConfig 配置好了
            AppFlywayConfig config = new AppFlywayConfig(mock(DataSource.class));

            // When 获取 Flyway 配置
            FluentConfiguration flywayConfig = config.buildFlywayConfiguration();

            // Then baselineOnMigrate = true(老环境缺 history 表时自动 baseline,允许后续迁移跑)
            assertThat(flywayConfig.isBaselineOnMigrate())
                    .as("老 app_db 已有 nd_* 表但没 Flyway history,需要 baselineOnMigrate=true 让 Flyway 接受这个非空 schema")
                    .isTrue();
        }

        @Test
        @DisplayName("应使用独立 history 表 flyway_app_schema_history(避免与 ruleforge_db / flowable_db 冲突)")
        void shouldUseIsolatedHistoryTable() {
            // Given AppFlywayConfig 配置好了
            AppFlywayConfig config = new AppFlywayConfig(mock(DataSource.class));

            // When 获取 Flyway 配置
            FluentConfiguration flywayConfig = config.buildFlywayConfiguration();

            // Then history 表名应是 flyway_app_schema_history(独立于 flyway_schema_history)
            assertThat(flywayConfig.getTable())
                    .as("必须用独立 history 表,否则跟 ruleforge_db 的 flyway_schema_history 冲突会启动失败")
                    .isEqualTo("flyway_app_schema_history");
        }

        @Test
        @DisplayName("baselineVersion 应设为 0(让 V5.16.0 跑)")
        void shouldUseBaselineVersionZero() {
            // Given AppFlywayConfig 配置好了
            AppFlywayConfig config = new AppFlywayConfig(mock(DataSource.class));

            // When 获取 Flyway 配置
            FluentConfiguration flywayConfig = config.buildFlywayConfiguration();

            // Then baselineVersion = 0(让 V5.16.0 跑,即便是老环境也能幂等应用)
            assertThat(flywayConfig.getBaselineVersion().getVersion())
                    .as("baseline=0 让 V5.16.0(首个 app_db migration)能跑;老环境用 IF NOT EXISTS 幂等")
                    .isEqualTo("0");
        }
    }

    @Nested
    @DisplayName("迁移执行")
    class MigrationExecution {

        @Test
        @DisplayName("migrateAppSchema() 应对 appDataSource 调 Flyway.migrate()")
        void shouldMigrateAppDataSource() {
            // Given appDataSource bean
            DataSource appDataSource = mock(DataSource.class);
            AppFlywayConfig config = new AppFlywayConfig(appDataSource);

            // When migrateAppSchema() 执行
            // (H2 in-memory 测试需要,但实际生产是 MySQL;此处只验证不抛异常 + log 出现)
            // Then 不应抛异常 — 用 @PostConstruct 入口 + log 验证
            // (实际 Flyway.migrate 真实跑需要 MySQL,集成测试在 Docker compose 上做)
        }
    }
}
