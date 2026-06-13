package com.ruleforge.executor.app.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.ruleforge.decision.config.InsertBatchSqlInjector;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * V5.53.3 — Executor MyBatis-Plus 配置:decision mapper 拆分。
 *
 * <p>之前: {@code @MapperScan("com.ruleforge.decision.mapper")} 全包绑到
 * ruleforgeSqlSessionFactory(ruleforge_db),但 8/9 lib mapper + 全部 10 个
 * executor-app 本地 mapper 实际指向 rfa_*(app_db),导致跨库 1146。
 *
 * <p>现在:
 * <ul>
 *   <li>{@code com.ruleforge.decision.mapper.rf} → ruleforgeSqlSessionFactory
 *       — 只装 GrayStrategyMapper(rf_gray_strategy)</li>
 *   <li>{@code com.ruleforge.decision.mapper} (parent) +
 *       executor-app 本地 10 个 mapper → appSqlSessionFactory
 *       — 装 rfa_* 全套(DecisionFlowState + DecisionLog 系列 + Shadow 系列)</li>
 *   <li>clickhouse 子包单独走 clickhouseSqlSessionFactory(在 ClickHouseMybatisPlusConfig)</li>
 * </ul>
 */
@Configuration
@MapperScan(value = "com.ruleforge.decision.mapper.rf", sqlSessionFactoryRef = "ruleforgeSqlSessionFactory")
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

    /**
     * V5.53.3 — 新增 appSqlSessionFactory(appDataSource = ruleforge_app_db)。
     * 给 decision parent mapper(8 个 lib mapper)+ executor-app 本地 10 个
     * log mapper 走 app_db。
     */
    @Bean
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

    /**
     * V5.53.3 — 把所有 rfa_* mapper 路由到 appSqlSessionFactory。
     *
     * <p>覆盖:
     * <ul>
     *   <li>{@code com.ruleforge.decision.mapper} — 8 个 lib mapper(Datasource/DecisionFlowState/RuleVariableDef/ShadowComparison/ShadowConfig)</li>
     *   <li>executor-app 本地 10 个 mapper(DecisionLog + ShadowLog 系列)</li>
     * </ul>
     *
     * <p>clickhouse 子包不在这里,在 ClickHouseMybatisPlusConfig。
     */
    @Bean
    public MapperScannerConfigurer appMapperScanner() {
        MapperScannerConfigurer cfg = new MapperScannerConfigurer();
        cfg.setBasePackage("com.ruleforge.decision.mapper");
        cfg.setSqlSessionFactoryBeanName("appSqlSessionFactory");
        return cfg;
    }
}
