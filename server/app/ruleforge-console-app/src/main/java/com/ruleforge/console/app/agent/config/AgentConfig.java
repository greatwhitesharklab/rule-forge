package com.ruleforge.console.app.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置属性。
 *
 * <p>在 application.yml 中配置：
 * <pre>
 * agent:
 *   enabled: true
 *   llm:
 *     base-url: https://api.openai.com/v1
 *     api-key: sk-xxx
 *     model: gpt-4o
 *     max-tokens: 4096
 *     temperature: 0.7
 *   system-prompt: "你是 RuleForge 风控规则分析助手..."
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {

    /**
     * 是否启用 Agent 功能
     */
    private boolean enabled = false;

    private Llm llm = new Llm();

    /**
     * 系统提示词（Agent 角色设定）
     */
    private String systemPrompt = """
            你是 RuleForge 风控规则分析助手。你可以帮助用户：
            1. 分析决策日志和执行趋势
            2. 查看规则覆盖率，识别未触发的规则
            3. 检测异常决策模式
            4. 导出和查看规则包内容
            5. 查看监控指标和告警
            6. 分析仿真结果和对比数据

            请用中文回答。如果需要数据，请使用提供的工具查询。
            """;

    @Data
    public static class Llm {
        /**
         * LLM API 基础 URL（兼容 OpenAI 格式）
         */
        private String baseUrl = "https://api.openai.com/v1";

        /**
         * API Key
         */
        private String apiKey = "";

        /**
         * 模型名称
         */
        private String model = "gpt-4o";

        /**
         * 最大生成 token 数
         */
        private int maxTokens = 4096;

        /**
         * 温度参数
         */
        private double temperature = 0.7;

        /**
         * 连接超时（毫秒）
         */
        private int connectTimeout = 10000;

        /**
         * 读取超时（毫秒）
         */
        private int readTimeout = 120000;
    }
}
