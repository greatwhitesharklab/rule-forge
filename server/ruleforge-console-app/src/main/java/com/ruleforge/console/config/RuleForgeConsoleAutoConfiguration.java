package com.ruleforge.console.config;

import com.ruleforge.console.service.RepositoryInterceptor;
import com.ruleforge.console.service.impl.DefaultRepositoryInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;


/**
 * @author fred
 * @since 2021/11/09 7:23 PM
 */
@Configuration
@PropertySource("classpath:ruleforge-console-context.properties")
@ImportResource("classpath:ruleforge-console-context.xml")
@ComponentScan(basePackages = {
        "com.ruleforge.console.config",
        "com.ruleforge.console.controller",
        "com.ruleforge.console.service",
        "com.ruleforge.console.service.impl",
        "com.ruleforge.console.repository",
        "com.ruleforge.console.storage",
        "com.ruleforge.console.storage.impl",
        "com.ruleforge.console.flow",
        "com.ruleforge.console.model",
        // Spring Boot 4 不扫 nested jar 的 @Component,补齐决策模块的所有包:
        //   config     — FlowableConfig 等 @Configuration
        //   connector  — 5 个数据源连接器(AdvanceAi/Jdbc/Rest/Pkl + TokenManager)
        //   repository — DatasourceRepositoryImpl(包装 mappers 给 Service 用)
        // decision 的 service.impl 由 RuleForgeConsoleApplication 显式 @Import,
        // decision 的 mapper 由 console-app 的 @MapperScan 显式扫。
        "com.ruleforge.decision.config",
        "com.ruleforge.decision.connector",
        "com.ruleforge.decision.repository"
})
@MapperScan(basePackages = {
        "com.ruleforge.console.mapper"
})
public class RuleForgeConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RepositoryInterceptor repositoryInterceptor() {
        return new DefaultRepositoryInterceptor();
    }

}
