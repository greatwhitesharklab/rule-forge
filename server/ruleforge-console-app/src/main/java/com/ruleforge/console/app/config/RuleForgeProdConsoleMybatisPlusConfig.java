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
        mapperScannerConfigurer.setBasePackage("com.ruleforge.console.app.mapper");
        mapperScannerConfigurer.setSqlSessionFactoryBeanName("appSqlSessionFactory");
        return mapperScannerConfigurer;
    }
}
