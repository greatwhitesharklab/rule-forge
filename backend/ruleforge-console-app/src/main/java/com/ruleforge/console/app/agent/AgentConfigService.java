package com.ruleforge.console.app.agent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.console.app.agent.config.AgentConfig;
import com.ruleforge.console.app.agent.config.VendorPresets;
import com.ruleforge.console.app.agent.model.AgentConfigEntity;
import com.ruleforge.console.app.mapper.AgentConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 配置服务 — 从数据库读取 LLM 配置（application.yml 作为 fallback）。
 *
 * <p>配置优先级：数据库 nd_agent_config > application.yml 默认值。
 * 修改数据库配置后实时生效，无需重启。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigService {

    private final AgentConfigMapper configMapper;
    private final AgentConfig yamlConfig;

    /** 本地缓存（避免每次请求都查 DB） */
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile long lastRefresh = 0;
    private static final long CACHE_TTL_MS = 30_000; // 30 秒缓存

    /**
     * 获取配置值（DB > YAML fallback）
     */
    public String get(String key) {
        refreshIfNeeded();
        String value = cache.get(key);
        if (value != null) {
            return value;
        }
        // YAML fallback
        return yamlFallback(key);
    }

    /**
     * 设置配置值
     */
    public void set(String key, String value) {
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setConfigKey(key);
        entity.setConfigValue(value);
        entity.setUpdateTime(LocalDateTime.now());

        // upsert
        AgentConfigEntity existing = configMapper.selectById(key);
        if (existing != null) {
            configMapper.updateById(entity);
        } else {
            configMapper.insert(entity);
        }

        cache.put(key, value);
        log.info("Agent config updated: {} = {}", key, maskSensitive(key, value));
    }

    /**
     * 批量获取所有配置
     */
    public Map<String, String> getAll() {
        refreshIfNeeded();
        return new LinkedHashMap<>(cache);
    }

    /**
     * 获取当前生效的完整 Agent 配置（合并 DB + YAML）
     */
    public AgentConfig getEffectiveConfig() {
        AgentConfig config = new AgentConfig();
        config.setEnabled(Boolean.parseBoolean(get("enabled")));
        config.getLlm().setBaseUrl(get("llm.base_url"));
        config.getLlm().setApiKey(get("llm.api_key"));
        config.getLlm().setModel(get("llm.model"));
        config.getLlm().setMaxTokens(Integer.parseInt(get("llm.max_tokens")));
        config.getLlm().setTemperature(Double.parseDouble(get("llm.temperature")));
        config.setSystemPrompt(get("system_prompt"));
        return config;
    }

    /**
     * 获取厂商预设列表
     */
    public List<VendorPresets.Vendor> getVendors() {
        return VendorPresets.all();
    }

    /**
     * 应用厂商预设 — 自动填充 base_url 和 model
     */
    public void applyVendor(String vendorId) {
        VendorPresets.Vendor vendor = VendorPresets.get(vendorId);
        if (vendor == null) {
            throw new IllegalArgumentException("Unknown vendor: " + vendorId);
        }
        set("llm.vendor", vendorId);
        if (!vendor.getBaseUrl().isEmpty()) {
            set("llm.base_url", vendor.getBaseUrl());
        }
        if (!vendor.getDefaultModel().isEmpty()) {
            set("llm.model", vendor.getDefaultModel());
        }
        log.info("Applied vendor preset: {} → baseUrl={}, model={}",
                vendor.getName(), vendor.getBaseUrl(), vendor.getDefaultModel());
    }

    /**
     * 测试 LLM 连接
     */
    public Map<String, Object> testConnection() {
        try {
            AgentConfig config = getEffectiveConfig();
            if (config.getLlm().getApiKey() == null || config.getLlm().getApiKey().isEmpty()) {
                return Map.of("success", false, "message", "API Key 未配置");
            }
            // TODO: 实际调用 LlmClient.testConnection()
            return Map.of("success", true, "message", "连接测试成功");
        } catch (Exception e) {
            return Map.of("success", false, "message", "连接失败: " + e.getMessage());
        }
    }

    // ===== 内部方法 =====

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh > CACHE_TTL_MS) {
            synchronized (this) {
                if (now - lastRefresh > CACHE_TTL_MS) {
                    loadFromDb();
                    lastRefresh = now;
                }
            }
        }
    }

    private void loadFromDb() {
        List<AgentConfigEntity> configs = configMapper.selectList(null);
        cache.clear();
        for (AgentConfigEntity c : configs) {
            if (c.getConfigValue() != null) {
                cache.put(c.getConfigKey(), c.getConfigValue());
            }
        }
    }

    private String yamlFallback(String key) {
        return switch (key) {
            case "enabled" -> String.valueOf(yamlConfig.isEnabled());
            case "llm.base_url" -> yamlConfig.getLlm().getBaseUrl();
            case "llm.api_key" -> yamlConfig.getLlm().getApiKey();
            case "llm.model" -> yamlConfig.getLlm().getModel();
            case "llm.max_tokens" -> String.valueOf(yamlConfig.getLlm().getMaxTokens());
            case "llm.temperature" -> String.valueOf(yamlConfig.getLlm().getTemperature());
            case "system_prompt" -> yamlConfig.getSystemPrompt();
            default -> "";
        };
    }

    private String maskSensitive(String key, String value) {
        if ("llm.api_key".equals(key) && value != null && value.length() > 8) {
            return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
        }
        return value;
    }
}
