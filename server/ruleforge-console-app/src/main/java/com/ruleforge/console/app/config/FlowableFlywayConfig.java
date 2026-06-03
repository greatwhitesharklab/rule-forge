package com.ruleforge.console.app.config;

import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Flowable 专用 Flyway 迁移,跑在 flowable_db 上。
 *
 * <p>为什么需要这个:
 * <ul>
 *   <li>Flowable 8.0.0 自带的 init SQL(create index 在 create table 之前)
 *       在 Spring Boot 4 + MySQL 8 下顺序错,直接 init 失败</li>
 *   <li>FlowableProperties(8.0.0)没有 datasource 字段,yml 里的
 *       flowable.datasource=flowable 被忽略,真正决定走哪个 DataSource
 *       的是 {@link FlowableConfig}</li>
 *   <li>所以我们让 Flyway 接管 act_* 表的初始化,关掉 Flowable 自 init</li>
 * </ul>
 *
 * <p>注意这个类放在 ruleforge-console-app 模块(而不是 ruleforge-decision),
 * 因为 console-app 是启动类所在的模块,@ComponentScan 一定能扫到。
 * 放在 decision 模块的 nested jar 里 @ComponentScan 不一定能扫到(我们的 console-app
 * 的扫描只到 basePackages,Spring Boot 4 对 nested jar 的 classpath 扫描行为有变化)。
 *
 * <p>用 {@code @PostConstruct} 而非 {@code @Bean Flyway} 是因为 Spring 的
 * @Bean 方法懒加载,没东西注入 Flyway bean 就不跑,表就建不出来。
 * PostConstruct 在 config bean 实例化后立刻跑,确保 Flyway migrate 早于
 * Flowable Engine bean 创建。
 */
@Configuration
public class FlowableFlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlowableFlywayConfig.class);

    private final DataSource flowableDataSource;

    public FlowableFlywayConfig(@Qualifier("flowable") DataSource flowableDataSource) {
        this.flowableDataSource = flowableDataSource;
    }

    @PostConstruct
    public void migrateFlowableSchema() {
        try {
            logger.info("Initializing Flowable schema via Flyway on flowable DataSource");
            Flyway flyway = Flyway.configure()
                    .dataSource(flowableDataSource)
                    .locations("classpath:db/migration-flowable")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .table("flowable_flyway_history")
                    .load();
            var result = flyway.migrate();
            logger.info("Flowable Flyway migration completed: {} migrations applied, success={}",
                    result.migrationsExecuted, result.success);
        } catch (Exception e) {
            logger.error("Flowable Flyway migration FAILED", e);
            throw new RuntimeException("Flowable schema init failed", e);
        }
    }
}
