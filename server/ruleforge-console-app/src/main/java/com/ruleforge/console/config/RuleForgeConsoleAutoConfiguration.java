package com.ruleforge.console.config;

import com.ruleforge.console.service.RepositoryInterceptor;
import com.ruleforge.console.service.impl.DefaultRepositoryInterceptor;
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
        // V5.8.0: BatchTest 多态化的包(Subject / InputSource / Orchestrator / Controller)
        "com.ruleforge.console.batchtest",
        // V5.10-B: 老项目 DB→Git migration tool (Service / Controller / CommandLineRunner)
        "com.ruleforge.console.migration",
        // V5.10-C: dualWrite 失败可观测 (Controller / RepositoryImpl)
        "com.ruleforge.console.observability",
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
public class RuleForgeConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RepositoryInterceptor repositoryInterceptor() {
        return new DefaultRepositoryInterceptor();
    }

}
