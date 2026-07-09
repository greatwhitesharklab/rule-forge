package com.ruleforge.datasource.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * RuleForge Datasource 模块的 AutoConfiguration(V7.21 从 ruleforge-decision 拆出)。
 *
 * <p>Spring Boot 4 的 ClassPathScanner 默认不扫 BOOT-INF/lib/*.jar 里的 @Component 类,
 * 必须显式 @Import 这个 config 才能让 datasource 模块的 @Service/@Component 被拾取。
 *
 * <p>包扫描范围(精简:无 flow/shadow/gray,纯数据源 + 变量定义):
 * <ul>
 *   <li>com.ruleforge.datasource.service.impl — DatasourceServiceImpl / RuleVariableDefServiceImpl</li>
 *   <li>com.ruleforge.datasource.connector — 7 个数据源连接器</li>
 *   <li>com.ruleforge.datasource.mapper — MyBatis mapper(@MapperScan)</li>
 * </ul>
 */
@Configuration
@ComponentScan(basePackages = {
        "com.ruleforge.datasource.service.impl",
        "com.ruleforge.datasource.connector",
})
@MapperScan(basePackages = {
        "com.ruleforge.datasource.mapper"
})
public class RuleForgeDatasourceAutoConfiguration {
}
