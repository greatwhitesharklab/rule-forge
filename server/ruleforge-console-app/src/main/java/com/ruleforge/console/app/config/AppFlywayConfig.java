package com.ruleforge.console.app.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * V5.16 app_db 专用 Flyway 迁移,跑在 app_db 上。
 *
 * <p>为什么需要这个:
 * <ul>
 *   <li>V5.16 之前 app_db 没有 Flyway 管理 — 11 张 nd_* 表(V5.1.x batchtest /
 *       V5.3.x agent / V5.6.x monitoring / ...)分布零散,版本不可追溯,新增/改字段只能
 *       手 ALTER,易踩坑</li>
 *   <li>Spring Boot 4 + 多数据源下 {@code spring.flyway.*} 只能绑一个 DataSource,
 *       这里跟 {@link com.ruleforge.console.config.FlywayConfig}(ruleforge_db)
 *       一样,直接 {@code Flyway.configure()} 拿到目标 DataSource 自己跑</li>
 * </ul>
 *
 * <p>隔离措施:
 * <ul>
 *   <li>独立 location: {@code classpath:db/migration-app}(跟 ruleforge_db 的
 *       {@code db/migration} 不混)</li>
 *   <li>独立 history 表: {@code flyway_app_schema_history}(跟
 *       {@code flyway_schema_history} 分开,避免跨库 schema 互相干扰)</li>
 *   <li>{@code baselineOnMigrate=true} + {@code baselineVersion="0"}:老 app_db
 *       已经有 11 张表但没 history,首次启动时 Flyway 在 0 建立 baseline,V5.16.0
 *       的 DDL 全用 {@code CREATE TABLE IF NOT EXISTS} 幂等跳过;新部署则从 0
 *       跑起建表</li>
 * </ul>
 *
 * <p>V5.21+: {@code db/migration-flowable} 目录保留作为 schema 文档参考,
 * 但不再有 Bean 引用它(已删除 {@code FlowableFlywayConfig});PR 4 会新建
 * V5.20.x 删表 SQL + 删除该目录。
 */
@Configuration
public class AppFlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppFlywayConfig.class);

    /** app_db Flyway history 表名,跟 ruleforge_db / flowable_db 隔离 */
    static final String HISTORY_TABLE = "flyway_app_schema_history";

    /** app_db migration 脚本位置(独立于 db/migration 和 db/migration-flowable) */
    static final String MIGRATION_LOCATION = "classpath:db/migration-app";

    /** 老环境 baseline 版本 — 设为 0 让 V5.16.0 能跑(老环境 IF NOT EXISTS 幂等) */
    static final String BASELINE_VERSION = "0";

    private final DataSource appDataSource;

    public AppFlywayConfig(@Qualifier("appDataSource") DataSource appDataSource) {
        this.appDataSource = appDataSource;
    }

    /** 测试用:暴露内部 DataSource */
    DataSource appDataSource() {
        return appDataSource;
    }

    /**
     * 构造 Flyway 配置(无副作用,纯 builder)。
     *
     * <p>独立成方法是为了单元测试可以检查 locations / table / baselineVersion
     * 等参数,不需要真的连 MySQL 跑 migrate。
     */
    FluentConfiguration buildFlywayConfiguration() {
        return Flyway.configure()
                .dataSource(appDataSource)
                .locations(MIGRATION_LOCATION)
                .baselineOnMigrate(true)
                .baselineVersion(BASELINE_VERSION)
                .table(HISTORY_TABLE);
    }

    @PostConstruct
    public void migrateAppSchema() {
        try {
            logger.info("Initializing app_db schema via Flyway (location={}, historyTable={})",
                    MIGRATION_LOCATION, HISTORY_TABLE);
            Flyway flyway = buildFlywayConfiguration().load();
            var result = flyway.migrate();
            logger.info("app_db Flyway migration completed: {} migrations applied, success={}",
                    result.migrationsExecuted, result.success);
        } catch (Exception e) {
            logger.error("app_db Flyway migration FAILED", e);
            throw new RuntimeException("app_db schema init failed", e);
        }
    }
}
