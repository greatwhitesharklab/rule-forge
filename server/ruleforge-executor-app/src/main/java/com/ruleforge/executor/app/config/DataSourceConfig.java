package com.ruleforge.executor.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Executor 数据源配置 — ruleforge + flowable 两个库。
 *
 * <p>和 console-app 的 DataSourceConfig 用同样模式,绕开 Spring Boot 4
 * 的 DataSourceAutoConfiguration,用 @Value + 直接 new HikariDataSource
 * 才能拿到显式的 poolName。
 */
@Configuration
public class DataSourceConfig {

    @Value("${RF_DB_URL}")
    private String rfDbUrl;

    @Value("${RF_DB_USERNAME:root}")
    private String rfDbUsername;

    @Value("${RF_DB_PASSWORD:}")
    private String rfDbPassword;

    @Value("${FLOWABLE_DB_URL}")
    private String flowableDbUrl;

    @Value("${FLOWABLE_DB_USERNAME:root}")
    private String flowableDbUsername;

    @Value("${FLOWABLE_DB_PASSWORD:}")
    private String flowableDbPassword;

    @Primary
    @Bean
    public DataSource ruleforgeDataSource() {
        return buildPool("ExecutorCloudSqlCP", rfDbUrl, rfDbUsername, rfDbPassword, 10);
    }

    @Bean("flowable")
    public DataSource flowableDataSource() {
        return buildPool("ExecutorFlowableSqlCP", flowableDbUrl, flowableDbUsername, flowableDbPassword, 5);
    }

    private HikariDataSource buildPool(String name, String jdbcUrl, String user, String pwd, int maxPool) {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName(name);
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(user);
        ds.setPassword(pwd);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setMaximumPoolSize(maxPool);
        ds.setMinimumIdle(Math.max(1, maxPool / 5));
        ds.setConnectionTimeout(60_000);
        ds.setIdleTimeout(300_000);
        ds.setMaxLifetime(1_200_000);
        return ds;
    }
}
