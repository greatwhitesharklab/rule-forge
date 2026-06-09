package com.ruleforge.console.app.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * Scan decision module mappers using ruleforge datasource
 * (ruleforgeSqlSessionFactory is defined in ruleforge-console's MybatisPlusConfig)
 */
@Configuration
@MapperScan(value = "com.ruleforge.decision.mapper", sqlSessionFactoryRef = "ruleforgeSqlSessionFactory")
public class DecisionMybatisPlusConfig {
}
