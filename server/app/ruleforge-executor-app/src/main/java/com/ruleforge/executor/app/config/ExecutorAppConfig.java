package com.ruleforge.executor.app.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Executor App 通用配置。
 *
 * <p>显式声明 {@link MeterRegistry} Bean。原因和 console-app 的
 * {@code ObjectMapper} 一样 — Spring Boot 4 + MyBatis-Plus 3.5.9
 * 间接破坏自动配置,导致 {@code DecisionServiceImpl} 注入
 * {@code MeterRegistry} 时找不到 Bean,executor 启动崩溃。
 */
@Configuration
public class ExecutorAppConfig {

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
