package com.ruleforge.executor.app.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Phase 8: ClickHouse 分析数据源 MyBatis 配置 (executor-app).
 *
 * <p>将 {@code com.ruleforge.decision.mapper.clickhouse} 包下的 mapper
 * 绑定到 ClickHouse SqlSessionFactory,用于双写。
 *
 * <p>通过 {@code clickhouse.analytics.enabled} 控制。
 */
@Configuration
@ConditionalOnProperty(name = "clickhouse.analytics.enabled", havingValue = "true", matchIfMissing = true)
@MapperScan(
        value = "com.ruleforge.decision.mapper.clickhouse",
        sqlSessionFactoryRef = "clickhouseSqlSessionFactory"
)
public class ClickHouseMybatisPlusConfig {

    @Bean
    public SqlSessionFactory clickhouseSqlSessionFactory(
            @Qualifier("clickhouseDataSource") DataSource clickhouseDataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(clickhouseDataSource);
        return sessionFactory.getObject();
    }
}
