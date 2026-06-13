package com.ruleforge.console.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

import com.ruleforge.decision.config.RuleForgeDecisionAutoConfiguration;
import com.ruleforge.decision.service.impl.DatasourceServiceImpl;
import com.ruleforge.decision.service.impl.GrayStrategyServiceImpl;
import com.ruleforge.decision.service.impl.RuleVariableDefServiceImpl;
import com.ruleforge.decision.service.impl.ShadowConfigServiceImpl;


@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
// 原 ruleforge-console 模块的 208 个文件全部合进 console-app,
// 控制器/service/mapper/storage/repository/config 都在 com.ruleforge.console.* 包下,
// @SpringBootApplication 默认从本类的 com.ruleforge.console.app 包开始扫,
// 同时覆盖到 com.ruleforge.console 父包,所以 console 内部不需要再 @Import 旁路。
//
// 决策模块(ruleforge-decision)仍在 nested jar 里,Spring Boot 4 下需要:
// - @Import 它的 Service 实现(boot 不会扫 nested jar 里的 @Component)
// - @MapperScan 显式注册它的 mapper 接口 — 由 DecisionMybatisPlusConfig 统一注册
// - @Import RuleForgeDecisionAutoConfiguration 启用 lib 内的 @Component (BpmnXmlParser /
//   FlowDefinitionRepo / NodeExecutor / 等),boot 不会扫 nested jar 里的 @Component
//
// V5.47 — ruleforge-dsl module 整 module 删除,.ul 老 DSL 链彻底下架,
//   RuleForgeDslAutoConfiguration 跟 4 个 DSL bean 全部不存在,不再 @Import。
@Import({
        DatasourceServiceImpl.class,
        GrayStrategyServiceImpl.class,
        RuleVariableDefServiceImpl.class,
        ShadowConfigServiceImpl.class,
        RuleForgeDecisionAutoConfiguration.class
})
public class RuleForgeConsoleApplication {

    public static void main(String[] main) {
        SpringApplication.run(RuleForgeConsoleApplication.class, main);
    }
}
