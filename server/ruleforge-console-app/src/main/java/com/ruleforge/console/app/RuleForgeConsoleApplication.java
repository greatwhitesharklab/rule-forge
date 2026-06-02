package com.ruleforge.console.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

import com.ruleforge.console.config.RuleForgeConsoleAutoConfiguration;


@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ImportAutoConfiguration(RuleForgeConsoleAutoConfiguration.class)
public class RuleForgeConsoleApplication {

    public static void main(String[] main) {
        SpringApplication.run(RuleForgeConsoleApplication.class, main);
    }
}
