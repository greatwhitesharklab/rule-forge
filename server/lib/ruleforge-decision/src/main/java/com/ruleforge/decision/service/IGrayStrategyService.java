package com.ruleforge.decision.service;

import com.ruleforge.decision.dto.GrayResolution;
import com.ruleforge.decision.entity.GrayStrategy;

import java.util.List;

/**
 * 灰度策略服务
 */
public interface IGrayStrategyService {

    /**
     * 解析请求应该使用的规则包版本
     *
     * @param packagePath 原始 packagePath (projectName/packageId)
     * @param userId      用户ID，用于 PERCENT_USER 和 WHITELIST 匹配
     * @return GrayResolution 包含 gitTag 和是否命中灰度
     */
    GrayResolution resolveVersion(String packagePath, String userId);

    /**
     * 查询指定包的灰度策略列表
     */
    List<GrayStrategy> listStrategies(Long projectId, String packageId);

    /**
     * 创建灰度策略
     */
    GrayStrategy createStrategy(GrayStrategy strategy);

    /**
     * 更新灰度策略
     */
    GrayStrategy updateStrategy(GrayStrategy strategy);

    /**
     * 删除灰度策略
     */
    void deleteStrategy(Long id);

    /**
     * 启用/停用灰度策略
     */
    void toggleStrategy(Long id, boolean enabled);
}
