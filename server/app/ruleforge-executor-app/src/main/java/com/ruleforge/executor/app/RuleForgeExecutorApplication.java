package com.ruleforge.executor.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import com.ruleforge.datasource.config.RuleForgeDatasourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {
        "com.ruleforge.executor",
        // V7.21 — ruleforge-decision 模块删除后,app 内仅剩 com.ruleforge.decision.lazy
        // (DatasourceRoutingProvider / LazyEntityFactory 等懒加载层,V1 数据源拉取要用)。
        "com.ruleforge.decision",
        // V7.21 — ruleforge-datasource 模块的 @Component(service.impl/connector/repository)。
        // 主类显式 @ComponentScan 会限制注册范围,AutoConfig 的 @ComponentScan 被覆盖,
        // 故此处直接扫 datasource 包(替代 RuleForgeDatasourceAutoConfiguration 的 ComponentScan;
        // @MapperScan 仍由 AutoConfig 承担)。
        "com.ruleforge.datasource",
        // Spring Boot 4 不扫 nested jar 的 @Component;补 core 模块的 cache 包
        // (MemoryKnowledgeCache — KnowledgePackageServiceImpl 依赖)
        "com.ruleforge.runtime.cache"
})
// V7.21 — ruleforge-decision 删除后,共享层在 ruleforge-datasource 模块。
// Spring Boot 4 不扫 nested jar 的 @Component,显式 @Import datasource 模块的
// AutoConfiguration(扫 connector / service.impl + @MapperScan datasource.mapper)。
// datasource.mapper 通过 appSqlSessionFactory(@Primary in MybatisPlusConfig)落 app_db。
@Import({RuleForgeDatasourceAutoConfiguration.class})
public class RuleForgeExecutorApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleForgeExecutorApplication.class, args);
    }
}
