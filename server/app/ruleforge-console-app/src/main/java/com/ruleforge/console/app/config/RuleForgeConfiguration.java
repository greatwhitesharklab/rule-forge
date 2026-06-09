package com.ruleforge.console.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
class RuleForgeConfiguration {

    /**
     * 显式声明 ObjectMapper Bean。
     *
     * <p>MyBatis-Plus 3.5.9 在 Spring Boot 4 下的 {@code MybatisPlusAutoConfiguration}
     * 通过 {@code @AutoConfigureAfter(DataSourceAutoConfiguration.class)} 引用了
     * 已迁移/重命名的自动配置类,导致 Spring 启动时报
     * {@code TypeNotPresentException},间接让 {@code JacksonAutoConfiguration}
     * 没机会注册,进而 {@code LlmClient} 注入 {@code ObjectMapper} 失败。
     *
     * <p>显式声明一个最小可用的实例,绕过这个间接破坏。
     * {@code MeterRegistry} 已在 {@code MetricsConfig} 中提供,不要重复声明。
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
