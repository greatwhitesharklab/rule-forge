package com.ruleforge.console.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * @author fred
 * 2023/08/30 5:15 PM
 */
@Configuration
@MapperScan(value = "com.ruleforge.console.mapper", sqlSessionFactoryRef = "ruleforgeSqlSessionFactory")
public class MybatisPlusConfig {

    @Bean
    public SqlSessionFactory ruleforgeSqlSessionFactory(@Qualifier("ruleforgeDataSource") DataSource ruleforgeDataSource) throws Exception {
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
