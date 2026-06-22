package com.ruleforge.config;

import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceLibraryBuilder;
import com.ruleforge.plugin.EnginePluginRegistry;
import com.ruleforge.runtime.service.KnowledgePackageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Feature: V6.13.4f ruleforge-core-context.xml 完整加载 smoke test
 *
 * <p>背景:ruleforge-core-context.xml 经 {@code RuleForgeCoreAutoConfiguration}(@ImportResource
 * + V6.13.4f 加的 @ComponentScan)被所有 Spring Boot 宿主加载,但此前**无任何测试加载完整
 * XML context** —— core 单测都是纯单元测试,导致 V5.76 stale class(runtime.rete.ValueCompute /
 * runtime.assertor.AssertorEvaluator)+ V6.13.4a ref 断(ruleforge.builtInActionLibraryBuilder)
 * 潜伏成定时炸弹,靠旧 core 5.0.0 jar 苇着。
 *
 * <p>V6.13.4f 修了这 3 处。本 smoke test 加载真正的 {@link RuleForgeCoreAutoConfiguration}
 * (跟 production 同款:@ImportResource XML + @ComponentScan config 包),锁住"所有 bean 能创建 +
 * ref 不断 + @Component 注册",防止 XML class 路径过时 / bean id 不匹配 / @Component 漏注册 这类
 * fallout 再潜伏。
 *
 * <p>Given RuleForgeCoreAutoConfiguration(V6.13.4f 修后:@ImportResource + @ComponentScan)
 * When  ApplicationContextRunner 加载它(allow-circular-references=true 模拟 production application.yml)
 * Then  context 不失败 + 核心 bean(pluginRegistry / resourceLibraryBuilder / knowledgeBuilder)存在
 *   And  @ComponentScan 注册 BuiltInActionLibraryBuilder(resourceLibraryBuilder 的 ref 不断)
 *   And  @PostConstruct 触发(pluginRegistry.getDebugWriters 扫到 XML 里的 DebugWriter bean)
 */
@DisplayName("V6.13.4f — ruleforge-core-context.xml 完整加载 smoke test")
class CoreContextSmokeTest {

    /**
     * core 的 {@link KnowledgeServiceImpl}(@RequiredArgsConstructor)ctor 注入
     * {@link KnowledgePackageService},但它注入的是 app 层的实现(console-app 的
     * KnowledgePackageServiceImpl),core 单独加载没有 → 提供一个 @Primary mock 解析注入
     * (模拟 app 层 bean,让 core context 能完整加载)。
     */
    @Configuration
    static class AppLayerStubs {
        @Bean
        @Primary
        KnowledgePackageService knowledgePackageService() {
            return mock(KnowledgePackageService.class);
        }
    }

    @Test
    @DisplayName("完整 core context 加载:所有 bean 创建 + ref 不断 + @ComponentScan + @PostConstruct")
    void shouldLoadCompleteCoreContextViaAutoConfiguration() {
        new ApplicationContextRunner()
                // 加载真正的 production auto-config(@ImportResource XML + @ComponentScan config 包)
                .withUserConfiguration(RuleForgeCoreAutoConfiguration.class)
                // 补 app 层 stub(KnowledgePackageService)—— core context 固有依赖,production 靠 app 提供
                .withUserConfiguration(AppLayerStubs.class)
                // 模拟 production application.yml 的 spring.main.allow-circular-references=true
                // (valueParser↔complexArithmeticParser 等解析器 setter 循环是设计,非 bug)
                .withAllowCircularReferences(true)
                .run(ctx -> {
                    // Then:context 不失败(V5.76 stale class + V6.13.4a ref 断都修了)
                    assertThat(ctx).hasNotFailed();

                    // And:核心 bean 都存在(XML ref 链完整)
                    assertThat(ctx).hasBean("ruleforge.pluginRegistry");
                    assertThat(ctx).hasBean("ruleforge.resourceLibraryBuilder");
                    assertThat(ctx).hasBean("ruleforge.knowledgeBuilder");

                    // And:ResourceLibraryBuilder 装配成功(其 builtInActionLibraryBuilder 依赖
                    //   由 V6.13.4f 修复:BuiltInActionLibraryBuilder @Component 显式 name
                    //   "ruleforge.builtInActionLibraryBuilder" 匹配 XML ref + @ComponentScan 注册)
                    assertThat(ctx.getBean(ResourceLibraryBuilder.class)).isNotNull();
                    assertThat(ctx).hasBean("ruleforge.builtInActionLibraryBuilder");

                    // And:@PostConstruct 触发(pluginRegistry init() 扫到 XML 里的 DebugWriter bean)
                    EnginePluginRegistry registry = ctx.getBean("ruleforge.pluginRegistry", EnginePluginRegistry.class);
                    assertThat(registry.getDebugWriters()).isNotEmpty();

                    // And:KnowledgeBuilder 装配完整(依赖链 resourceLibraryBuilder → builtInActionLibraryBuilder)
                    assertThat(ctx.getBean(KnowledgeBuilder.class)).isNotNull();
                });
    }
}

