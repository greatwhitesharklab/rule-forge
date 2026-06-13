package com.ruleforge.console.app.config;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * @author fred
 * @since 2020/08/18 4:39 PM
 */
@Configuration
public class RuleForgeProdConsoleMybatisPlusConfig {

    @Bean
    @org.springframework.context.annotation.Primary
    public SqlSessionFactory appSqlSessionFactory(@Qualifier("appDataSource") DataSource appDataSource) throws Exception {
        MybatisSqlSessionFactoryBean sessionFactory = new MybatisSqlSessionFactoryBean();
        sessionFactory.setDataSource(appDataSource);
        return sessionFactory.getObject();
    }

    @Bean
    public MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer mapperScannerConfigurer = new MapperScannerConfigurer();
        // V5.53.3 — 把 decision 模块的 mapper 也加进来(除了 GrayStrategyMapper 它
        // 在 com.ruleforge.decision.mapper.rf 子包,走 ruleforgeSqlSessionFactory)。
        // 原因:V5.53.2 重命名 nd_→rfa_ 后,8/9 个 lib mapper 实际指向 app_db 的 rfa_*
        // 表,但 @MapperScan 一直把它们绑到 ruleforgeSqlSessionFactory → 跨库查询 1146。
        // GrayStrategyMapper 是唯一还在 ruleforge_db(rf_gray_strategy)的,已移到子包。
        mapperScannerConfigurer.setBasePackage(
                "com.ruleforge.console.app.mapper,"
                + "com.ruleforge.decision.mapper");
        mapperScannerConfigurer.setSqlSessionFactoryBeanName("appSqlSessionFactory");
        return mapperScannerConfigurer;
    }
}
