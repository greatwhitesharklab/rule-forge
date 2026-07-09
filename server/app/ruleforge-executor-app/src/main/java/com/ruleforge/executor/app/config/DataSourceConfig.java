package com.ruleforge.executor.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Executor 数据源配置 — ruleforge / app。
 *
 * <p>和 console-app 的 DataSourceConfig 用同样模式,绕开 Spring Boot 4
 * 的 DataSourceAutoConfiguration,用 @Value + 直接 new HikariDataSource
 * 才能拿到显式的 poolName。
 *
 * <p>V7.21 — BPMN/陪跑/灰度/ClickHouse analytics 全部删除:
 * <ul>
 *   <li>ruleforge_db(rf_*):executor 现仅作底层连接池保留,无 rf_ mapper 读取</li>
 *   <li>app_db(rfa_*):datasource 模块的 mapper 走 appSqlSessionFactory(@Primary)</li>
 *   <li>clickhouse:analytics 双写已删,clickhouseDataSource bean 移除</li>
 * </ul>
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

    @Primary
    @Bean
    public DataSource ruleforgeDataSource() {
        return buildPool("ExecutorCloudSqlCP", rfDbUrl, rfDbUsername, rfDbPassword, 10);
    }

    @Bean
    public DataSource appDataSource() {
        return buildPool("ExecutorAppCP", appDbUrl, appDbUsername, appDbPassword, 10);
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
