package com.ruleforge.console.app.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * @author fred
 * @since 2020/08/18 4:39 PM
 */
@Configuration
public class RuleForgeProdConsoleMybatisPlusConfig {

    @Bean
    @org.springframework.context.annotation.Primary
    public SqlSessionFactory appSqlSessionFactory(@Qualifier("appDataSource") DataSource appDataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(appDataSource);
        return sessionFactory.getObject();
    }

    @Bean
    public MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer mapperScannerConfigurer = new MapperScannerConfigurer();
        // V7.21 — ruleforge-decision 删除后,原 decision.mapper(rfa_* 表)迁到
        // com.ruleforge.datasource.mapper,由 RuleForgeDatasourceAutoConfiguration 的
        // @MapperScan 统一扫(绑定到 appSqlSessionFactory — 本类的 @Primary SqlSessionFactory)。
        // 本 scanner 只扫 console-app 本地 mapper(audit/batchtest/simulation 等)。
        mapperScannerConfigurer.setBasePackage("com.ruleforge.console.app.mapper");
        mapperScannerConfigurer.setSqlSessionFactoryBeanName("appSqlSessionFactory");
        return mapperScannerConfigurer;
    }
}
