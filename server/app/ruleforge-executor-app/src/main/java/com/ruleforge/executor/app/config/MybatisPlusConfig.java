package com.ruleforge.executor.app.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ruleforge.datasource.config.InsertBatchSqlInjector;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * V7.21 — Executor MyBatis-Plus 配置(BPMN/陪跑/灰度删除后收口)。
 *
 * <p>背景:ruleforge-decision 模块彻底删除,共享层迁到 ruleforge-datasource。
 * datasource 模块的 mapper(com.ruleforge.datasource.mapper)指向 app_db 的
 * rfa_* 表(rfa_datasource / rfa_rule_variable_def / rfa_datasource_log 等)。
 *
 * <p>datasource 模块的 {@code RuleForgeDatasourceAutoConfiguration} 自带
 * {@code @MapperScan("com.ruleforge.datasource.mapper")} 但未指定
 * sqlSessionFactoryRef,因此会绑到 PRIMARY 的 SqlSessionFactory。
 * 这里把 {@code appSqlSessionFactory}(app_db)声明为 @Primary,使 datasource
 * mapper 自动落到 app_db,无需 executor 再重复 @MapperScan。
 *
 * <p>ruleforgeSqlSessionFactory(ruleforge_db)在 V7.21 后只剩给 ruleforge_db
 * 做底层连接池(rf_* 表为 console 侧管理,executor 现不再读 rf_gray_strategy),
 * 保留为非 primary 以兼容现有 DataSourceConfig 注入。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
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

    /**
     * V7.21 — appSqlSessionFactory(appDataSource = ruleforge_app_db),@Primary。
     * datasource 模块的 mapper(rfa_* 表)通过模块自带 @MapperScan 落到这里。
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public SqlSessionFactory appSqlSessionFactory(
            @Qualifier("appDataSource") DataSource appDataSource) throws Exception {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        GlobalConfig globalConfig = new GlobalConfig().setSqlInjector(new InsertBatchSqlInjector());

        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(appDataSource);
        sessionFactory.setGlobalConfig(globalConfig);
        sessionFactory.setPlugins(interceptor);
        return sessionFactory.getObject();
    }
}
