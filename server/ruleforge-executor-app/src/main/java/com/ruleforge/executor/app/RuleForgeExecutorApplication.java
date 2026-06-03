package com.ruleforge.executor.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {
        "com.ruleforge.executor",
        "com.ruleforge.decision",
        // Spring Boot 4 不扫 nested jar 的 @Component;补 core 模块的 cache 包
        // (MemoryKnowledgeCache — KnowledgePackageServiceImpl 依赖)
        "com.ruleforge.runtime.cache"
})
public class RuleForgeExecutorApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleForgeExecutorApplication.class, args);
    }
}
