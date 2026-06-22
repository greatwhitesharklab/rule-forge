package com.ruleforge.config;

import com.ruleforge.debug.DefaultHtmlFileDebugWriter;
import com.ruleforge.plugin.EnginePluginRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature: V6.13.4e SpringEnginePluginRegistry 装配链路验证
 *
 * <p>核心单测不启动 Spring context,故 {@code SpringEnginePluginRegistry} 从
 * {@code ApplicationContextAware} + {@code setApplicationContext} 回调改成
 * ctor 注入 {@link ApplicationContext} + {@code @PostConstruct} 这条链路无覆盖。
 * 本测试用 {@link ApplicationContextRunner}(Spring Boot context,自动注册 BPP,
 * 会触发 {@code @PostConstruct})锁定 V6.13.4e 改动:
 *
 * <p>Given SpringEnginePluginRegistry 改 ctor 注入 ctx + @PostConstruct init()
 * When  ApplicationContextRunner 启动(模拟 Spring Boot 宿主 + XML autowire=constructor)
 * Then  @PostConstruct init() 自动触发 → getBeansOfType 扫到注册的 DebugWriter bean
 *
 * <p>注:不用完整 ruleforge-core-context.xml 加载 —— 那个 XML 有独立的 V5.76/V6.13.4a
 * fallout(AssertorEvaluator/ValueCompute stale class 已修;builtInActionLibraryBuilder
 * ref 断留 follow-up),跟本类改动无关,本测试聚焦 registry 装配链路。
 */
@DisplayName("SpringEnginePluginRegistry V6.13.4e — ctor 注入 ctx + @PostConstruct init")
class SpringEnginePluginRegistryContextTest {

    /** 最小 @Configuration:模拟 XML 的 pluginRegistry bean(ctor 注入 ctx)+ 一个 DebugWriter bean。 */
    @Configuration
    static class TestConfig {
        @Bean
        DefaultHtmlFileDebugWriter debugWriter() {
            return new DefaultHtmlFileDebugWriter();
        }

        @Bean("ruleforge.pluginRegistry")
        SpringEnginePluginRegistry pluginRegistry(ApplicationContext ctx) {
            // 模拟 XML autowire="constructor":Spring 按类型把当前 ctx 注入 ctor
            return new SpringEnginePluginRegistry(ctx);
        }
    }

    @Test
    @DisplayName("context 启动应触发 @PostConstruct init() 扫到注册的 DebugWriter bean")
    void shouldRunPostConstructInitAndScanPluginsOnContextStart() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .run(ctx -> {
                    // Then:bean 存在(ctor 注入 ctx 成功,否则 Spring 创建 bean 失败)
                    EnginePluginRegistry registry = ctx.getBean("ruleforge.pluginRegistry", EnginePluginRegistry.class);
                    assertThat(registry).isNotNull();

                    // And:@PostConstruct init() 被 Spring 自动触发 → getBeansOfType(DebugWriter.class)
                    // 扫到 TestConfig 注册的 bean(若 init() 没触发,getDebugWriters() 返字段默认值 emptyList)
                    assertThat(registry.getDebugWriters())
                            .hasSize(1)
                            .first()
                            .isInstanceOf(DefaultHtmlFileDebugWriter.class);
                });
    }
}
