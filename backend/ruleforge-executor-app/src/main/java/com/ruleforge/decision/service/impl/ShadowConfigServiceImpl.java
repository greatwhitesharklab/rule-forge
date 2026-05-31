package com.ruleforge.decision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.decision.entity.ShadowConfig;
import com.ruleforge.decision.mapper.ShadowConfigMapper;
import com.ruleforge.decision.service.IShadowConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 陪跑配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowConfigServiceImpl implements IShadowConfigService {

    private final ShadowConfigMapper shadowConfigMapper;

    @Override
    public List<ShadowConfig> findEnabledByMainPath(String mainRulePackagePath) {
        return shadowConfigMapper.findEnabledByMainPath(mainRulePackagePath);
    }

    @Override
    public boolean shouldExecuteShadow(ShadowConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        Integer sampleRate = config.getSampleRate();
        if (sampleRate == null || sampleRate <= 0) {
            return false;
        }
        if (sampleRate >= 100) {
            return true;
        }
        // 随机采样
        int random = ThreadLocalRandom.current().nextInt(100);
        return random < sampleRate;
    }

    // ===== CRUD 方法 =====

    @Override
    public List<ShadowConfig> listAll() {
        return shadowConfigMapper.selectList(new LambdaQueryWrapper<ShadowConfig>()
                .orderByDesc(ShadowConfig::getId));
    }

    @Override
    public ShadowConfig getById(Long id) {
        return shadowConfigMapper.selectById(id);
    }

    @Override
    public ShadowConfig create(ShadowConfig config) {
        config.setId(null);
        config.setEnabled(true);
        shadowConfigMapper.insert(config);
        log.info("创建陪跑配置: id={}, mainPath={}, shadowPath={}, sampleRate={}",
                config.getId(), config.getMainRulePackagePath(),
                config.getShadowRulePackagePath(), config.getSampleRate());
        return config;
    }

    @Override
    public ShadowConfig update(ShadowConfig config) {
        shadowConfigMapper.updateById(config);
        log.info("更新陪跑配置: id={}", config.getId());
        return config;
    }

    @Override
    public void delete(Long id) {
        shadowConfigMapper.deleteById(id);
        log.info("删除陪跑配置: id={}", id);
    }

    @Override
    public void toggle(Long id, boolean enabled) {
        ShadowConfig config = shadowConfigMapper.selectById(id);
        if (config != null) {
            config.setEnabled(enabled);
            shadowConfigMapper.updateById(config);
            log.info("陪跑配置 {}: id={}", enabled ? "启用" : "停用", id);
        }
    }
}
