package com.ruleforge.console.app.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Phase 8: ClickHouse 分析数据源 MyBatis 配置.
 *
 * <p>将 {@code com.ruleforge.console.app.mapper.clickhouse} 包下的 mapper
 * 绑定到 ClickHouse SqlSessionFactory,与 MySQL 的 ruleforgeSqlSessionFactory 隔离。
 *
 * <p>通过 {@code clickhouse.analytics.enabled} 配置开关控制:
 * 设为 false 时不创建任何 bean,analytics 查询自动回退到 MySQL。
 */
@Configuration
@ConditionalOnProperty(name = "clickhouse.analytics.enabled", havingValue = "true", matchIfMissing = true)
@MapperScan(
        value = "com.ruleforge.console.app.mapper.clickhouse",
        sqlSessionFactoryRef = "clickhouseSqlSessionFactory"
)
public class ClickHouseMybatisPlusConfig {

    @Bean
    public SqlSessionFactory clickhouseSqlSessionFactory(
            @Qualifier("clickhouseDataSource") DataSource clickhouseDataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(clickhouseDataSource);
        // ClickHouse 不需要 PaginationInnerInterceptor — analytics mapper 用原生 @Select
        return sessionFactory.getObject();
    }
}
