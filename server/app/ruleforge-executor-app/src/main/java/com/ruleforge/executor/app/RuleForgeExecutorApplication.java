package com.ruleforge.executor.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {
        "com.ruleforge.executor",
        "com.ruleforge.decision",
        // Spring Boot 4 不扫 nested jar 的 @Component;补 core 模块的 cache 包
        // (MemoryKnowledgeCache — KnowledgePackageServiceImpl 依赖)
        "com.ruleforge.runtime.cache"
})
// V5.47 — ruleforge-dsl module 整 module 删除,.ul 老 DSL 链彻底下架,
// 无 DSL bean 可 @Import。KnowledgeBuilder 的 dslRuleSetBuilder 字段在 V5.45.4
// 已删,KnowledgeBuilder 通过 KnowledgeBuilder .ul 老格式走 0 rule fallback
// (V5.43 行为保留)。
@Import({})
public class RuleForgeExecutorApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleForgeExecutorApplication.class, args);
    }
}
