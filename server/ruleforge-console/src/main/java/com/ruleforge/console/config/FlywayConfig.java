package com.ruleforge.console.config;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

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
