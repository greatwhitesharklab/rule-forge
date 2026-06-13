package com.ruleforge.executor.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Executor 数据源配置 — ruleforge / app / clickhouse。
 *
 * <p>和 console-app 的 DataSourceConfig 用同样模式,绕开 Spring Boot 4
 * 的 DataSourceAutoConfiguration,用 @Value + 直接 new HikariDataSource
 * 才能拿到显式的 poolName。
 *
 * <p>V5.21+: flowable DataSource 已删除 — 决策流由自建 FlowEngine 驱动,
 * 不再走 Flowable 8 引擎,flowable_db 也不再被任何 Bean 引用。
 *
 * <p>V5.53.3 — 新增 appDataSource(ruleforge_app_db)供 appSqlSessionFactory 使用。
 * 之前 executor-app 只连 ruleforge_db,导致 DecisionFlowStateMapper 等指向
 * rfa_* 的 mapper 跨库查询 1146。现在 ruleforge_db 给 GrayStrategyMapper(rf_gray_strategy),
 * app_db 给其余所有 decision mapper。
 */
@Configuration
public class DataSourceConfig {

    @Value("${APP_DB_URL}")
    private String appDbUrl;

    @Value("${APP_DB_USERNAME:root}")
    private String appDbUsername;

    @Value("${APP_DB_PASSWORD:}")
    private String appDbPassword;

    @Value("${RF_DB_URL}")
    private String rfDbUrl;

    @Value("${RF_DB_USERNAME:root}")
    private String rfDbUsername;

    @Value("${RF_DB_PASSWORD:}")
    private String rfDbPassword;

    @Value("${CH_DB_URL:jdbc:clickhouse://192.168.3.36:8123/ruleforge_analytics}")
    private String chDbUrl;

    @Value("${CH_DB_USERNAME:default}")
    private String chDbUsername;

    @Value("${CH_DB_PASSWORD:changeme}")
    private String chDbPassword;

    @Primary
    @Bean
    public DataSource ruleforgeDataSource() {
        return buildPool("ExecutorCloudSqlCP", rfDbUrl, rfDbUsername, rfDbPassword, 10);
    }

    @Bean
    public DataSource appDataSource() {
        return buildPool("ExecutorAppCP", appDbUrl, appDbUsername, appDbPassword, 10);
    }

    @Bean("clickhouseDataSource")
    public DataSource clickhouseDataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setPoolName("ClickHouseCP");
        ds.setJdbcUrl(chDbUrl);
        ds.setUsername(chDbUsername);
        ds.setPassword(chDbPassword);
        ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        ds.setMaximumPoolSize(5);
        ds.setMinimumIdle(1);
        ds.setConnectionTimeout(10_000);
        ds.setIdleTimeout(300_000);
        ds.setMaxLifetime(600_000);
        return ds;
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
