package com.ruleforge.console.app.datasource;

import com.ruleforge.datasource.DataSourceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * V5.23 — Spring configuration for the data source subsystem.
 *
 * <p>Wires the lib's {@link DataSourceRegistry} with console-app's
 * {@link DataSourceAuditLogImpl}. Same wiring exists in executor-app with its own
 * audit impl (each app writes to its own DB).
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSourceRegistry dataSourceRegistry(DataSourceAuditLogImpl auditLog) {
        return new DataSourceRegistry(auditLog);
    }
}
