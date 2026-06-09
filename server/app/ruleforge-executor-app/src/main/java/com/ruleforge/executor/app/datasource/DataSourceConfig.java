package com.ruleforge.executor.app.datasource;

import com.ruleforge.datasource.DataSourceRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * V5.23 — Spring config for executor-app's data source subsystem.
 *
 * <p>Mirrors the console-side {@code DataSourceConfig} but with executor's own
 * audit log impl. Each app supplies its own {@code DataSourceAuditLog} bean.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    public DataSourceRegistry dataSourceRegistry(ExecutorDataSourceAuditLog auditLog) {
        return new DataSourceRegistry(auditLog);
    }
}
