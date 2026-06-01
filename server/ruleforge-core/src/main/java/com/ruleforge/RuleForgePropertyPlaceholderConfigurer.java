package com.ruleforge;

import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;

public class RuleForgePropertyPlaceholderConfigurer extends PropertyPlaceholderConfigurer {
    public RuleForgePropertyPlaceholderConfigurer() {
        setIgnoreUnresolvablePlaceholders(true);
        setOrder(100);
    }
}
