package com.ruleforge.console.app.agent.config;

import lombok.Data;

import java.util.*;

/**
 * LLM 厂商预设 — 用户选厂商后自动填充 base_url 和默认 model。
 *
 * <p>所有厂商均兼容 OpenAI Chat Completions API 格式。
 */
public class VendorPresets {

    private static final Map<String, Vendor> VENDORS = new LinkedHashMap<>();

    static {
        add("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o",
                "GPT-4o、GPT-4o-mini 等，需海外网络");
        add("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat",
                "DeepSeek-V3/R1，国内可直接访问");
        add("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
                "阿里通义千问，兼容 OpenAI 格式");
        add("zhipu", "智谱 AI", "https://open.bigmodel.cn/api/paas/v1", "glm-4-flash",
                "智谱 GLM-4 系列，兼容 OpenAI 格式");
        add("moonshot", "Moonshot", "https://api.moonshot.cn/v1", "moonshot-v1-8k",
                "Kimi 大模型，兼容 OpenAI 格式");
        add("baichuan", "百川智能", "https://api.baichuan-ai.com/v1", "Baichuan4",
                "百川大模型，兼容 OpenAI 格式");
        add("minimax", "MiniMax", "https://api.minimax.chat/v1", "MiniMax-Text-01",
                "MiniMax 大模型，兼容 OpenAI 格式");
        add("siliconflow", "SiliconFlow", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-72B-Instruct",
                "硅基流动，提供多种开源模型推理服务");
        add("ollama", "Ollama (本地)", "http://localhost:11434/v1", "qwen2.5:7b",
                "本地运行开源模型，无需 API Key");
        add("custom", "自定义", "", "",
                "手动填写 base_url 和 model");
    }

    private static void add(String id, String name, String baseUrl, String defaultModel, String description) {
        VENDORS.put(id, new Vendor(id, name, baseUrl, defaultModel, description));
    }

    /** 获取所有厂商列表 */
    public static List<Vendor> all() {
        return new ArrayList<>(VENDORS.values());
    }

    /** 按 ID 获取厂商预设 */
    public static Vendor get(String vendorId) {
        return VENDORS.get(vendorId);
    }

    /** 厂商预设信息 */
    @Data
    public static class Vendor {
        private final String id;
        private final String name;
        private final String baseUrl;
        private final String defaultModel;
        private final String description;
    }
}
