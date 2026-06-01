package com.ruleforge.console.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication
@ComponentScan(basePackages = {"com.ruleforge.console", "com.ruleforge.decision"})
public class RuleForgeConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuleForgeConsoleApplication.class, args);
    }
}