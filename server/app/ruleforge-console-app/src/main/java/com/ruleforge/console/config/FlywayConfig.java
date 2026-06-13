package com.ruleforge.console.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * ruleforge_db Flyway bean。
 * <p>
 * V5.53.2:app_db 的 Flyway 走 {@code com.ruleforge.console.app.config.AppFlywayConfig},
 * 跟本类同一个 @Configuration 不能挂两个 @Bean 同时绑不同 DataSource(会撞 bean 依赖)。
 * ruleforge_db 跟 ruleforge_app_db 各自独立的 flyway_schema_history 表,不会互相干扰。
 * <p>
 * 重要:本类在 com.ruleforge.console.config 包下,不在 console-app 的
 * {@code @SpringBootApplication} 默认扫描范围,必须由
 * {@link com.ruleforge.console.app.RuleForgeConsoleApplication} 显式
 * {@code @Import(FlywayConfig.class)} 拉进 context。{@code RuleForgeConsoleAutoConfiguration}
 * 上的 {@code @ComponentScan(basePackages = "com.ruleforge.console.config")} 不生效 —
 * Spring Boot 主类的 scan 会覆盖嵌套 scan 路径。
 */
@Configuration
public class FlywayConfig {

    private static final Logger logger = LoggerFactory.getLogger(FlywayConfig.class);

    @Autowired
    private FlywayProperties flywayProperties;

    @Bean
    public Flyway flyway(@Qualifier("ruleforgeDataSource") DataSource dataSource) {
        if (!flywayProperties.isEnabled()) {
            logger.info("Flyway is disabled, skipping database migration");
            return null;
        }

        logger.info("Initializing Flyway for RuleForge Console database migration");

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(flywayProperties.getLocations())
                .baselineOnMigrate(flywayProperties.isBaselineOnMigrate())
                .baselineVersion(flywayProperties.getBaselineVersion())
                .baselineDescription(flywayProperties.getBaselineDescription())
                .validateOnMigrate(flywayProperties.isValidateOnMigrate())
                .outOfOrder(flywayProperties.isOutOfOrder())
                .table(flywayProperties.getTable())
                .encoding(flywayProperties.getEncoding())
                .load();

        try {
            flyway.migrate();
            logger.info("Flyway migration completed successfully");
        } catch (Exception e) {
            logger.error("Flyway migration failed", e);
            throw e;
        }

        return flyway;
    }
}
