package com.ruleforge.config;
import com.ruleforge.runtime.cache.KnowledgeCache;
import com.ruleforge.runtime.cache.MemoryKnowledgeCache;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * V6.13.4a: 去除 {@code ApplicationContextAware} — 改 {@code @Component} + 构造注入
 * {@link List<KnowledgeCache>}。构造里直接选第一个 cache(或 fallback {@link MemoryKnowledgeCache}),
 * 不需要 {@code @PostConstruct}。
 *
 * <p>静态 {@code knowledgeCache} 字段保留以便 {@code CacheUtils.getKnowledgeCache()} 静态 API
 * 7 处 caller 不用改;新 caller 建议走构造注入实例而非静态访问。
 */
@Component
public class CacheUtils {
    @Getter
    private static KnowledgeCache knowledgeCache;

    public CacheUtils(List<KnowledgeCache> caches) {
        if (caches != null && !caches.isEmpty()) {
            CacheUtils.knowledgeCache = caches.get(0);
        } else {
            CacheUtils.knowledgeCache = new MemoryKnowledgeCache();
        }
    }
}
