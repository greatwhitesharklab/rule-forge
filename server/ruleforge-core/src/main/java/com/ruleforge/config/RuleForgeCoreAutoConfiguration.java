package com.ruleforge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;

/**
 * @author Fred
 * @date 2021/11/09 7:40 PM
 */
@Configuration
@PropertySource("classpath:ruleforge-core-context.properties")
@ImportResource({"classpath:ruleforge-core-context.xml"})
public class RuleForgeCoreAutoConfiguration {
}
