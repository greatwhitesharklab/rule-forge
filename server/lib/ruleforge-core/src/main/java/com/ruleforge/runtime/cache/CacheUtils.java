package com.ruleforge.runtime.cache;

import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collection;

public class CacheUtils implements ApplicationContextAware {
    @Getter
    private static KnowledgeCache knowledgeCache;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Collection<KnowledgeCache> caches = applicationContext.getBeansOfType(KnowledgeCache.class).values();
        if (!caches.isEmpty()) {
            CacheUtils.knowledgeCache = caches.iterator().next();
        } else {
            CacheUtils.knowledgeCache = new MemoryKnowledgeCache();
        }
    }
}
