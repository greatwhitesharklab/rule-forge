package com.ruleforge.console.app.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * V5.53.3 — Decision mapper 拆分:
 * <ul>
 *   <li>原 com.ruleforge.decision.mapper 全包扫,绑 ruleforgeSqlSessionFactory(ruleforge_db)</li>
 *   <li>V5.53.2 重命名 nd_→rfa_ 后,8/9 mapper 实际指向 app_db 的 rfa_* 表,
 *       GrayStrategyMapper 是唯一留在 ruleforge_db(rf_gray_strategy)的</li>
 *   <li>现在:GrayStrategyMapper 移到 com.ruleforge.decision.mapper.rf 子包,
 *       本配置只扫这个子包 → ruleforgeSqlSessionFactory(ruleforge_db)
 *       — 跟它唯一对应的 rf_gray_strategy 表同源</li>
 *   <li>其余 8 个 lib mapper(com.ruleforge.decision.mapper parent)以及
 *       10 个 executor-app 本地 mapper 都由 RuleForgeProdConsoleMybatisPlusConfig
 *       一起扫到 appSqlSessionFactory(appDataSource = app_db)</li>
 * </ul>
 */
@Configuration
@MapperScan(value = "com.ruleforge.decision.mapper.rf", sqlSessionFactoryRef = "ruleforgeSqlSessionFactory")
public class DecisionMybatisPlusConfig {
}
