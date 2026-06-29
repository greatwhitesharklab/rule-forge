package com.ruleforge.runtime.service;


import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.config.CacheUtils;
import com.ruleforge.runtime.cache.KnowledgeCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * V7.7.2:KnowledgePackageService 已删(老 .rp 知识包管线废弃),KnowledgeServiceImpl
 * 保留作为 Service 接口的占位实现 — fetchKnowledgePackage 返 null(无 remote 源),
 * 缓存命中仍正常返回。生产 V1 决策流走 V1FlowRunner 不经过这里。
 */
@Slf4j
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    private Long knowledgeUpdateCycle = 0L;

    /** V6.0:由 XML property 注入(原 @Value,去 Spring 注解) */
    public void setKnowledgeUpdateCycle(Long knowledgeUpdateCycle) {
        this.knowledgeUpdateCycle = knowledgeUpdateCycle;
    }

    @Override
    public KnowledgePackage[] getKnowledges(String[] packageIds) throws IOException {
        KnowledgePackage[] packages = new KnowledgePackage[packageIds.length];
        for (int i = 0; i < packageIds.length; i++) {
            String packageId = packageIds[i];
            packages[i] = getKnowledge(packageId);
        }
        return packages;
    }

    @Override
    public KnowledgePackage getKnowledge(String packageId) throws IOException {
        KnowledgeCache cache = CacheUtils.getKnowledgeCache();

        // V7.7.2:.rp 废弃 → 无 remote fetch 源,fetchKnowledgePackage 返 null
        // 走 fallback:cache 命中返缓存,否则 null。
        log.debug("KnowledgeServiceImpl.getKnowledge called for [{}], returning cache only (no .rp remote)", packageId);
        KnowledgePackage knowledgePackage = cache.getKnowledge(packageId);
        if (knowledgePackage == null) {
            log.warn("Knowledge package [{}] not in cache and no remote (.rp deprecated)", packageId);
        }
        return knowledgePackage;
    }
}
