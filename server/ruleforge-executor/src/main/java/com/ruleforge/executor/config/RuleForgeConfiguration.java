package com.ruleforge.executor.config;

import com.ruleforge.controller.KnowledgePackageReceiverServlet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RuleForgeConfiguration {

    @Bean
    public ServletRegistrationBean<KnowledgePackageReceiverServlet> registerRuleForgeServlet() {
        return new ServletRegistrationBean<>(new KnowledgePackageReceiverServlet(), "/ruleforge/*");
    }
}
