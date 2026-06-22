package com.ruleforge.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;

/**
 * @author Fred
 * @date 2021/11/09 7:40 PM
 *
 * <p>V6.13.4f: 加 {@code @ComponentScan("com.ruleforge.config")} —— 修 V6.13.4a 的装配漏洞。
 * V6.13.4a 把 5 个类(CacheUtils / BuiltInActionLibraryBuilder / FileResourceProvider /
 * PropertyConfigurer / BsfVariableCollector)从 XML bean 改 {@code @Component},但注册依赖
 * 宿主 app 的 component scan —— console-app 扫了 com.ruleforge.config,executor-app 没扫,
 * 导致这 5 个 @Component 在 executor-app 不注册。后果:ResourceLibraryBuilder 的 XML
 * {@code <property ref="ruleforge.builtInActionLibraryBuilder">} 找不到 bean(NoSuchBeanDefinition),
 * 完整 core context 加载炸 —— 定时炸弹(production 靠旧 core 5.0.0 jar 苟着)。
 *
 * <p>修法:core 自己负责扫描自己的 @Component(不靠每个 app 的 scan)。本 auto-config 经
 * {@code META-INF/spring/...AutoConfiguration.imports} 被所有 Spring Boot 宿主自动加载,
 * 在此加 @ComponentScan 即保证两个 app 都注册这 5 个 bean。excludeFilters 排除
 * {@code @Configuration} 避免扫描到本类自身。
 */
@Configuration
@PropertySource("classpath:ruleforge-core-context.properties")
@ImportResource({"classpath:ruleforge-core-context.xml"})
@ComponentScan(
        basePackages = "com.ruleforge.config",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = Configuration.class)
)
public class RuleForgeCoreAutoConfiguration {
}
