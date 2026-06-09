package com.ruleforge.runtime.service;


import com.ruleforge.exception.RuleException;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.cache.CacheUtils;
import com.ruleforge.runtime.cache.KnowledgeCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service("ruleforge.knowledgeService")
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    @Value("${ruleforge.knowledgeUpdateCycleV2:0}") // 使用 V2 配置，0:每次都从远程获取, >=1:周期检查(毫秒)
    private Long knowledgeUpdateCycle;
    private final KnowledgePackageService knowledgePackageService;

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

        // 优先处理 knowledgeUpdateCycle == 0 的情况
        if (this.knowledgeUpdateCycle == 0) {
            log.info("KnowledgeUpdateCycle is 0, forcing fetch for package [{}] regardless of cache state.", packageId);
            // 直接调用 fetch 并更新缓存
            return fetchAndUpdateCache(packageId, cache);
        }

        // 1. 检查脏标记 (如果 cycle != 0)
        if (cache.isKnowledgeDirty(packageId)) {
            log.info("Knowledge package [{}] is marked dirty, forcing fetch.", packageId);
            // 脏标记强制重新获取最新版本
            return fetchAndUpdateCache(packageId, cache);
        }

        // 2. 非脏且 cycle != 0，尝试从缓存获取
        KnowledgePackage knowledgePackage = cache.getKnowledge(packageId);
        if (knowledgePackage != null) {
            // 3. 缓存命中: 检查是否需要周期性检查远程更新 (cycle > 1)
            if (shouldUpdate(knowledgePackage)) {
                log.info("Knowledge package [{}] cache expired or needs periodic check, checking remote.", packageId);
                return checkAndUpdateCache(packageId, knowledgePackage, cache);
            } else {
                // 缓存命中且无需检查 (cycle => 1 但未超时)，直接返回
                log.debug("Knowledge package [{}] found in cache and is up-to-date.", packageId);
                return knowledgePackage;
            }
        } else {
            // 4. 缓存未命中 (且 cycle != 0): 执行获取流程
            // 因为 cycle == 0 的情况已在前面处理，这里一定是 cycle >= 1
            log.info("Knowledge package [{}] not found in cache, fetching from remote.", packageId);
            return fetchAndUpdateCache(packageId, cache);
        }
    }

    /**
     * 检查是否需要进行周期性的远程检查
     *
     * @param knowledgePackage 当前缓存的知识包
     * @return true 如果需要检查，false 则不需要
     */
    private boolean shouldUpdate(KnowledgePackage knowledgePackage) {
        // 仅当 knowledgeUpdateCycle > 1 时才启用周期检查
        if (this.knowledgeUpdateCycle <= 1) {
            return false;
        }
        long timestamp = knowledgePackage.getTimestamp();
        long now = System.currentTimeMillis();
        long elapsedTime = now - timestamp;
        return elapsedTime >= this.knowledgeUpdateCycle;
    }

    /**
     * 检查是否有更新，并相应地更新缓存
     *
     * @param packageId     包ID
     * @param cachedPackage 当前缓存的包
     * @param cache         缓存实例
     * @return 最新的知识包（可能是远程更新的，也可能是确认未更新的本地包）
     */
    private KnowledgePackage checkAndUpdateCache(String packageId, KnowledgePackage cachedPackage, KnowledgeCache cache) {
        try {
            log.debug("Checking updates for package [{}] since timestamp [{}] version [{}].",
                    packageId, cachedPackage.getTimestamp(), cachedPackage.getVersion());

            if (this.knowledgePackageService.isKnowledgePackageNeedUpdate(packageId)) {
                // 远程有更新
                log.info("Remote check found updated knowledge package [{}]. Updating cache.", packageId);
                return fetchAndUpdateCache(packageId, cache);
            } else {
                // 远程无更新，重置本地缓存时间戳避免频繁检查
                log.info("Remote check confirmed no updates for knowledge package [{}]. Resetting cache timestamp.", packageId);
                cachedPackage.resetTimestamp();
                cache.putKnowledge(packageId, cachedPackage); // 重新放入缓存以更新时间戳
                return cachedPackage;
            }
        } catch (Exception e) {
            // 检查远程时发生异常，返回当前缓存的版本，避免影响主流程
            log.warn("Exception occurred during remote check for package [{}]. Returning cached version.", packageId, e);
            return cachedPackage;
        }
    }

    /**
     * 从远程获取知识包并更新缓存
     *
     * @param packageId 包ID
     * @param cache     缓存实例
     * @return 获取到的知识包
     * @throws RuleException 如果获取失败
     */
    private KnowledgePackage fetchAndUpdateCache(String packageId, KnowledgeCache cache) throws IOException {
        try {
            KnowledgePackage fetchedPackage = fetchKnowledgePackage(packageId); // fetchKnowledgePackage 包含本地回退逻辑
            if (fetchedPackage != null) {
                log.info("Successfully fetched knowledge package [{}]. Updating cache.", packageId);
                // fetchKnowledgePackage 内部已 resetTimestamp
                cache.putKnowledge(packageId, fetchedPackage);
                // Clear dirty flag after successful fetch
                cache.clearKnowledgeDirty(packageId);
                return fetchedPackage;
            } else {
                // fetchKnowledgePackage 返回 null 表示远程和本地都获取失败
                log.error("Failed to fetch knowledge package [{}] from remote and local sources.", packageId);
//                throw new RuleException("Failed to fetch required knowledge package: " + packageId);
                return cache.getKnowledge(packageId);
            }
        } catch (IOException | RuleException e) { // 捕获 fetchKnowledgePackage 可能抛出的异常
            log.error("Exception occurred during fetch for package [{}].", packageId, e);
            throw new RuleException("Failed to fetch knowledge package due to exception: " + packageId, e);
        }
    }

    // fetchKnowledgePackage 方法保持不变
    private KnowledgePackage fetchKnowledgePackage(String packageId) throws IOException {
        // 调用 RemoteService (无环境参数版本)
        KnowledgePackage knowledgePackage = this.knowledgePackageService.buildKnowledgePackage(packageId);
        if (knowledgePackage == null) {
            log.error("Local KnowledgePackageService failed to build package [{}].", packageId);
        } else {
            knowledgePackage.resetTimestamp();
        }
        return knowledgePackage;
    }
}
