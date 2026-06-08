package com.ruleforge.console.app.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 多数据源配置:app / ruleforge / clickhouse。
 *
 * <p>注意:不能用 {@code DataSourceBuilder + @ConfigurationProperties},
 * Spring Boot 4 下 Hikari 池的 pool-name / maximum-pool-size 等属性绑不上,
 * 会退化成默认 HikariPool-1/HikariPool-2,看不出是哪个库的连接池。
 *
 * <p>改用 {@code @Value} 显式注入,然后直接 new {@link HikariDataSource} 并
 * 设置 poolName / connectionTimeout 等。这样既绕开 Spring Boot 4 的
 * {@link DataSourceAutoConfiguration}(已 exclude),又能精确控制每个池。
 *
 * <p>V5.21+: flowable DataSource 已删除 — 决策流由自建 FlowEngine 驱动,
 * 不再走 Flowable 8 引擎,flowable_db 也不再被任何 Bean 引用。
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
    public DataSource appDataSource() {
        return buildPool("AppCloudSqlCP", appDbUrl, appDbUsername, appDbPassword, 10);
    }

    @Bean
    public DataSource ruleforgeDataSource() {
        return buildPool("UruleCloudSqlCP", rfDbUrl, rfDbUsername, rfDbPassword, 10);
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
