package com.ruleforge.decision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.decision.dto.GrayResolution;
import com.ruleforge.decision.entity.GrayStrategy;
import com.ruleforge.decision.mapper.rf.GrayStrategyMapper;
import com.ruleforge.decision.service.IGrayStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 灰度策略服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrayStrategyServiceImpl implements IGrayStrategyService {

    private final GrayStrategyMapper grayStrategyMapper;

    /** 缓存: packagePath → 活跃策略列表 */
    private final ConcurrentHashMap<String, List<GrayStrategy>> strategyCache = new ConcurrentHashMap<>();

    private static final long CACHE_TTL_MS = 30_000; // 30 秒刷新
    private volatile long lastCacheRefresh = 0;

    /** 策略优先级: WHITELIST > PERCENT_USER > PERCENT_RANDOM */
    private static final Map<String, Integer> STRATEGY_PRIORITY = Map.of(
            "WHITELIST", 0,
            "PERCENT_USER", 1,
            "PERCENT_RANDOM", 2
    );

    @Override
    public GrayResolution resolveVersion(String packagePath, String userId) {
        // 从 packagePath 提取 packageId (格式: projectName/packageId)
        String packageId = extractPackageId(packagePath);

        List<GrayStrategy> strategies = getActiveStrategies(packageId);
        if (strategies == null || strategies.isEmpty()) {
            return null; // 无灰度策略，由调用方决定使用默认版本
        }

        // 按优先级排序: WHITELIST(0) → PERCENT_USER(1) → PERCENT_RANDOM(2)
        List<GrayStrategy> sorted = new ArrayList<>(strategies);
        sorted.sort(Comparator.comparingInt(s ->
                STRATEGY_PRIORITY.getOrDefault(s.getStrategyType(), 99)));

        // 按优先级遍历
        for (GrayStrategy strategy : sorted) {
            if (matchStrategy(strategy, userId)) {
                log.info("灰度命中: userId={}, packageId={}, strategyType={}, strategyId={}, targetGitTag={}",
                        userId, packageId, strategy.getStrategyType(), strategy.getId(), strategy.getTargetGitTag());
                return GrayResolution.gray(strategy.getTargetGitTag(), strategy.getId());
            }
        }

        // 未命中任何灰度策略，返回基准版本
        GrayStrategy first = strategies.get(0);
        return GrayResolution.baseline(first.getBaselineGitTag());
    }

    @Override
    public List<GrayStrategy> listStrategies(Long projectId, String packageId) {
        LambdaQueryWrapper<GrayStrategy> wrapper = new LambdaQueryWrapper<GrayStrategy>()
                .eq(projectId != null, GrayStrategy::getProjectId, projectId)
                .eq(packageId != null, GrayStrategy::getPackageId, packageId)
                .orderByDesc(GrayStrategy::getCreateTime);
        return grayStrategyMapper.selectList(wrapper);
    }

    @Override
    public GrayStrategy createStrategy(GrayStrategy strategy) {
        grayStrategyMapper.insert(strategy);
        invalidateCache();
        log.info("创建灰度策略: id={}, packageId={}, type={}", strategy.getId(), strategy.getPackageId(), strategy.getStrategyType());
        return strategy;
    }

    @Override
    public GrayStrategy updateStrategy(GrayStrategy strategy) {
        grayStrategyMapper.updateById(strategy);
        invalidateCache();
        log.info("更新灰度策略: id={}", strategy.getId());
        return strategy;
    }

    @Override
    public void deleteStrategy(Long id) {
        grayStrategyMapper.deleteById(id);
        invalidateCache();
        log.info("删除灰度策略: id={}", id);
    }

    @Override
    public void toggleStrategy(Long id, boolean enabled) {
        GrayStrategy strategy = grayStrategyMapper.selectById(id);
        if (strategy != null) {
            strategy.setEnabled(enabled);
            grayStrategyMapper.updateById(strategy);
            invalidateCache();
            log.info("灰度策略 {}: id={}", enabled ? "启用" : "停用", id);
        }
    }

    // ===== 内部方法 =====

    private boolean matchStrategy(GrayStrategy strategy, String userId) {
        if (!Boolean.TRUE.equals(strategy.getEnabled())) {
            return false;
        }

        String type = strategy.getStrategyType();
        switch (type) {
            case "WHITELIST":
                return matchWhitelist(strategy, userId);
            case "PERCENT_USER":
                return matchPercentUser(strategy, userId);
            case "PERCENT_RANDOM":
                return matchPercentRandom(strategy);
            default:
                log.warn("未知灰度策略类型: {}", type);
                return false;
        }
    }

    private boolean matchWhitelist(GrayStrategy strategy, String userId) {
        if (strategy.getWhitelist() == null || strategy.getWhitelist().isBlank() || userId == null) {
            return false;
        }
        Set<String> whitelist = Arrays.stream(strategy.getWhitelist().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return whitelist.contains(userId);
    }

    private boolean matchPercentUser(GrayStrategy strategy, String userId) {
        if (userId == null) {
            return false;
        }
        int percent = strategy.getGrayPercent() != null ? strategy.getGrayPercent() : 0;
        if (percent <= 0) return false;
        if (percent >= 100) return true;
        // 稳定哈希: 同一用户永远命中或永远不命中
        return Math.abs(userId.hashCode()) % 100 < percent;
    }

    private boolean matchPercentRandom(GrayStrategy strategy) {
        int percent = strategy.getGrayPercent() != null ? strategy.getGrayPercent() : 0;
        if (percent <= 0) return false;
        if (percent >= 100) return true;
        return ThreadLocalRandom.current().nextInt(100) < percent;
    }

    private List<GrayStrategy> getActiveStrategies(String packageId) {
        String cacheKey = packageId;

        // 检查缓存是否过期
        if (System.currentTimeMillis() - lastCacheRefresh > CACHE_TTL_MS) {
            strategyCache.clear();
            lastCacheRefresh = System.currentTimeMillis();
        }

        return strategyCache.computeIfAbsent(cacheKey, key -> {
            LambdaQueryWrapper<GrayStrategy> wrapper = new LambdaQueryWrapper<GrayStrategy>()
                    .eq(GrayStrategy::getPackageId, key)
                    .eq(GrayStrategy::getEnabled, 1)
                    .orderByAsc(GrayStrategy::getId);
            return grayStrategyMapper.selectList(wrapper);
        });
    }

    private void invalidateCache() {
        strategyCache.clear();
        lastCacheRefresh = 0;
    }

    private String extractPackageId(String packagePath) {
        if (packagePath == null) return null;
        // packagePath 格式: projectName/packageId
        int slash = packagePath.indexOf('/');
        return slash >= 0 ? packagePath.substring(slash + 1) : packagePath;
    }
}
