package com.ruleforge.executor.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.ruleforge.executor", "com.ruleforge.decision"})
public class RuleForgeExecutorApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleForgeExecutorApplication.class, args);
    }
}
