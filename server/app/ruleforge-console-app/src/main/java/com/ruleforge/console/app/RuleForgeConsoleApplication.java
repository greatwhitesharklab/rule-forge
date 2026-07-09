package com.ruleforge.console.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

import com.ruleforge.datasource.config.RuleForgeDatasourceAutoConfiguration;
// V5.53.2 — FlywayConfig 跟 RuleForgeConsoleAutoConfiguration 都在
//   com.ruleforge.console.config 包(@SpringBootApplication 扫不到),
//   @ComponentScan 写在那 2 个 @Configuration 类里也被主类的 scan 覆盖。
//   显式 @Import 才能让 ruleforge_db Flyway bean 进入 context。
import com.ruleforge.console.config.FlywayConfig;


@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
// 原 ruleforge-console 模块的 208 个文件全部合进 console-app,
// 控制器/service/mapper/storage/repository/config 都在 com.ruleforge.console.* 包下,
// @SpringBootApplication 默认从本类的 com.ruleforge.console.app 包开始扫,
// 同时覆盖到 com.ruleforge.console 父包,所以 console 内部不需要再 @Import 旁路。
//
// V7.21 — ruleforge-decision 模块彻底删除(BPMN 引擎 + 陪跑/灰度全砍):
//   - 共享层(数据源/变量定义)迁到 ruleforge-datasource 模块
//   - Spring Boot 4 不扫 nested jar 的 @Component,显式 @Import
//     RuleForgeDatasourceAutoConfiguration(扫 connector / service.impl + @MapperScan)
//   - DatasourceServiceImpl / RuleVariableDefServiceImpl 由 AutoConfig 的 @ComponentScan 拾取
//   - GrayStrategyServiceImpl / ShadowConfigServiceImpl 已随模块删除,不再 @Import
@Import({
        RuleForgeDatasourceAutoConfiguration.class,
        // V5.53.2 — 上面 FlywayConfig import 的同款,显式带进 context,见 import 段注释。
        FlywayConfig.class
})
public class RuleForgeConsoleApplication {

    public static void main(String[] main) {
        SpringApplication.run(RuleForgeConsoleApplication.class, main);
    }
}
