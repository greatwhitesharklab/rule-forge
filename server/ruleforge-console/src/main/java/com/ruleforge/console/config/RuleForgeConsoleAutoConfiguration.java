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
@ComponentScan(basePackages = "com.ruleforge.console")
public class RuleForgeConsoleAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RepositoryInterceptor repositoryInterceptor() {
        return new DefaultRepositoryInterceptor();
    }

}
