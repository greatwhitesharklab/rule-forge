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
        // 关闭 Flowable 自带 init,act_* 表由 {@link FlowableFlywayConfig} 走 Flyway 创建。
        // Spring Boot 4 + MySQL 8 下 Flowable 8.0.0 init SQL 有顺序 bug
        // (create index 在 create table 之前),跑不通。
        config.setDatabaseSchemaUpdate("false");
    }
}
