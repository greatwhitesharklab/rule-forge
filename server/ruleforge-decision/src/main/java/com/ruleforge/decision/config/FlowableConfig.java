package com.ruleforge.decision.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlowableConfig implements EngineConfigurationConfigurer<SpringProcessEngineConfiguration> {

    private final DataSource ruleforgeDataSource;

    public FlowableConfig(@Qualifier("flowable") DataSource flowableDataSource) {
        this.ruleforgeDataSource = flowableDataSource;
    }

    @Override
    public void configure(SpringProcessEngineConfiguration config) {
        config.setDataSource(ruleforgeDataSource);
        config.setDatabaseSchemaUpdate("true");
    }
}
