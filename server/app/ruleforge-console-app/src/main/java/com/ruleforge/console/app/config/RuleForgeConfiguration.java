package com.ruleforge.console.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.ir.drl.DrlIdeService;
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

    /**
     * V5.101 — 显式声明 {@link DrlIdeService} Bean。
     *
     * <p>{@code DrlIdeController} (V5.78 PR #142) 构造器注入 {@code DrlIdeService},但
     * {@code DrlIdeService} 在 core 模块 ({@code com.ruleforge.ir.drl}),按 CLAUDE.md
     * "核心引擎逻辑不渗 Spring" 设计**不带 @Service/@Component**,从未被 @Bean 注册过 →
     * app 从 V5.78 起启动失败 (从未跑 jar 验证)。 这里显式 @Bean 暴露 (跟 ObjectMapper
     * 同档 "显式 bean 声明" workaround), DrlIdeService 是无状态纯函数 service,bare new 即可。
     */
    @Bean
    public DrlIdeService drlIdeService() {
        return new DrlIdeService();
    }
}
