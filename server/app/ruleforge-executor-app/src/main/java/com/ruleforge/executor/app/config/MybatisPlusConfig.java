package com.ruleforge.executor.app.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ruleforge.decision.config.InsertBatchSqlInjector;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Executor MyBatis-Plus 配置 — 扫描 decision 模块的 mapper
 */
@Configuration
@MapperScan(value = "com.ruleforge.decision.mapper", sqlSessionFactoryRef = "ruleforgeSqlSessionFactory")
public class MybatisPlusConfig {

    @Bean
    @org.springframework.context.annotation.Primary
    public SqlSessionFactory ruleforgeSqlSessionFactory(
            @Qualifier("ruleforgeDataSource") DataSource ruleforgeDataSource) throws Exception {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        GlobalConfig globalConfig = new GlobalConfig().setSqlInjector(new InsertBatchSqlInjector());

        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(ruleforgeDataSource);
        sessionFactory.setGlobalConfig(globalConfig);
        sessionFactory.setPlugins(interceptor);
        return sessionFactory.getObject();
    }
}
