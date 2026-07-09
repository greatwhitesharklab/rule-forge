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
        // V6.13.3: 之前漏 com.ruleforge.console.util,导致 @Component EnvironmentUtils 永远不被
        // 实例化,setApplicationContext 永不跑,任何 EnvironmentUtils.getLoginUser() 调用
        // 触发 NPE (`applicationContext is null`) — 包括创建新项目等基础操作。
        "com.ruleforge.console.util",
        // V6.13.4a: 5 个 core config 类 (CacheUtils / BuiltInActionLibraryBuilder /
        // FileResourceProvider / PropertyConfigurer / BsfVariableCollector) 改 @Component
        // 后,必须显式加入 scan — core XML 没 <context:component-scan>,默认扫不到。
        "com.ruleforge.config",
        // V5.8.0: BatchTest 多态化的包(Subject / InputSource / Orchestrator / Controller)
        "com.ruleforge.console.batchtest",
        // V5.43.8 — 路线 B 收口删 com.ruleforge.console.migration 整包
        // V5.10-C: dualWrite 失败可观测 (Controller / RepositoryImpl)
        "com.ruleforge.console.observability",
        // V5.17: user/permission audit log (Entity / Mapper / Service / Controller)
        "com.ruleforge.console.audit"
        // V7.21 — ruleforge-decision 模块删除,原 decision.config / connector / repository
        // 三包已不存在。数据源连接器 + DatasourceRepositoryImpl 迁到 ruleforge-datasource 模块,
        // 由 RuleForgeDatasourceAutoConfiguration(在 RuleForgeConsoleApplication @Import)
        // 的 @ComponentScan / @MapperScan 统一处理,本类不再扫 decision 包。
})
// V5.14 cleanup: 移除重复 @MapperScan — 由 MybatisPlusConfig 统一扫
// (com.ruleforge.console.mapper + com.ruleforge.console.audit.mapper)
public class RuleForgeConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RepositoryInterceptor repositoryInterceptor() {
        return new DefaultRepositoryInterceptor();
    }

}
