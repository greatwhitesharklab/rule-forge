package com.ruleforge.decision.lazy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 延迟加载配置类
 *
 * DatasourceRoutingProvider 由 @Service 自动注册，实现 DataSourceProvider 接口。
 * LazyEntityFactory 由下面的 Bean 方法创建，注入 DatasourceRoutingProvider。
 */
@Slf4j
@Configuration
public class LazyLoadConfig {

    @Bean
    @ConditionalOnProperty(name = "lazy-load.enabled", havingValue = "true")
    public LazyEntityFactory lazyEntityFactory(DatasourceRoutingProvider routingProvider) {
        log.info("Initializing LazyEntityFactory with DatasourceRoutingProvider");
        return new LazyEntityFactory(routingProvider);
    }
}
