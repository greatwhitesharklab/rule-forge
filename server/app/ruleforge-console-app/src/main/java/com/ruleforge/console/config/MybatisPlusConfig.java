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
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * @author fred
 * 2023/08/30 5:15 PM
 */
@Configuration
@MapperScan(value = {
        "com.ruleforge.console.mapper",
        // V5.17: audit log mapper(走 ruleforgeSqlSessionFactory 跟 rf_user / rf_user_audit_log 同源)
        "com.ruleforge.console.audit.mapper",
        // V5.22: AI 规则草稿 mapper(rf_draft)
        "com.ruleforge.console.app.draft"
}, sqlSessionFactoryRef = "ruleforgeSqlSessionFactory")
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
        // V5.17: 显式声明 mapper XML 位置 — 默认 classpath*:**/mapper/**/*Mapper.xml
        // 在 MyBatis-Plus 3.5.x 下不可靠,AuditLogMapper.selectListByFilters 走 XML,
        // 不显式 setMapperLocations 会抛 "Invalid bound statement (not found)"。
        sessionFactory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/**/*.xml"));
        return sessionFactory.getObject();
    }
}
