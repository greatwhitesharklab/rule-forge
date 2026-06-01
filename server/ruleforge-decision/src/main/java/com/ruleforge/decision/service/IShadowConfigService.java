package com.ruleforge.decision.service;

import com.ruleforge.decision.entity.ShadowConfig;

import java.util.List;

/**
 * 陪跑配置服务接口
 */
public interface IShadowConfigService {

    /**
     * 根据主规则包路径查询启用的陪跑配置
     */
    List<ShadowConfig> findEnabledByMainPath(String mainRulePackagePath);

    /**
     * 判断是否应该执行陪跑（根据采样率）
     */
    boolean shouldExecuteShadow(ShadowConfig config);

    // ===== CRUD 方法 =====

    /** 查询所有陪跑配置 */
    List<ShadowConfig> listAll();

    /** 按 ID 查询 */
    ShadowConfig getById(Long id);

    /** 创建陪跑配置 */
    ShadowConfig create(ShadowConfig config);

    /** 更新陪跑配置 */
    ShadowConfig update(ShadowConfig config);

    /** 删除陪跑配置 */
    void delete(Long id);

    /** 切换启停状态 */
    void toggle(Long id, boolean enabled);
}
