package com.ruleforge.console.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Flyway配置属性
 *
 * @author Claude
 * @date 2025-10-01
 */
@Data
@Component
@ConfigurationProperties(prefix = "flyway")
public class FlywayProperties {

    /**
     * 是否启用Flyway
     */
    private boolean enabled = true;

    /**
     * 迁移脚本位置
     */
    private String[] locations = new String[]{"classpath:db/migration"};

    /**
     * 在迁移时是否设置基线
     */
    private boolean baselineOnMigrate = true;

    /**
     * 基线版本。
     *
     * <p>生产环境如果 gr_* 表结构已被外部脚本初始化,可设回 "3.0.0" 让 V3.0.0
     * 被当作基线跳过;开发/测试/CI 环境设为 "0",让 V3.0.0 也执行,
     * 否则 V3.1.3 等迁移会 alter 不到 V3.0.0 应该创建的表。
     */
    private String baselineVersion = "0";

    /**
     * 基线描述
     */
    private String baselineDescription = "Base version for RuleForge Console";

    /**
     * 迁移时是否校验
     */
    private boolean validateOnMigrate = false;

    /**
     * 是否允许乱序迁移
     */
    private boolean outOfOrder = false;

    /**
     * 是否禁用clean操作
     */
    private boolean cleanDisabled = true;

    /**
     * Flyway表名
     */
    private String table = "flyway_schema_history";

    /**
     * 编码格式
     */
    private String encoding = "UTF-8";
}