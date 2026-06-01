package com.ruleforge.decision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.decision.entity.ShadowConfig;
import com.ruleforge.decision.mapper.ShadowConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ShadowConfigServiceImpl 单元测试
 *
 * BDD 行为场景:
 *
 * 1. Given sampleRate=100, When shouldExecuteShadow, Then 返回 true
 * 2. Given sampleRate=0, When shouldExecuteShadow, Then 返回 false
 * 3. Given 3 条配置, When listAll, Then 返回 3 条
 * 4. Given 配置 enabled=true, When toggle(id, false), Then enabled=false
 * 5. Given 有效的 ShadowConfig, When create, Then 插入数据库并返回带 id 的实体
 * 6. Given 配置 id=1 存在, When delete(1), Then mapper.delete 被调用
 */
@ExtendWith(MockitoExtension.class)
class ShadowConfigServiceImplTest {

    @Mock
    private ShadowConfigMapper shadowConfigMapper;

    private ShadowConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShadowConfigServiceImpl(shadowConfigMapper);
    }

    private ShadowConfig buildConfig(Long id, String mainPath, String shadowPath, int sampleRate, boolean enabled) {
        ShadowConfig config = new ShadowConfig();
        config.setId(id);
        config.setMainRulePackagePath(mainPath);
        config.setShadowRulePackagePath(shadowPath);
        config.setSampleRate(sampleRate);
        config.setEnabled(enabled);
        return config;
    }

    // ===== 场景 1: 采样率 100% =====

    @Nested
    @DisplayName("采样率 100%")
    class SampleRate100 {

        @Test
        @DisplayName("Given sampleRate=100, When shouldExecuteShadow, Then 返回 true")
        void sampleRate100_alwaysTrue() {
            ShadowConfig config = buildConfig(1L, "project/pkg1", "project/pkg1-v2", 100, true);

            assertTrue(service.shouldExecuteShadow(config));
        }
    }

    // ===== 场景 2: 采样率 0% =====

    @Nested
    @DisplayName("采样率 0%")
    class SampleRate0 {

        @Test
        @DisplayName("Given sampleRate=0, When shouldExecuteShadow, Then 返回 false")
        void sampleRate0_alwaysFalse() {
            ShadowConfig config = buildConfig(1L, "project/pkg1", "project/pkg1-v2", 0, true);

            assertFalse(service.shouldExecuteShadow(config));
        }
    }

    // ===== 场景 3: 查询全部 =====

    @Nested
    @DisplayName("查询所有配置")
    class ListAll {

        @Test
        @DisplayName("Given 3 条配置, When listAll, Then 返回 3 条")
        void listAll_returnsAll() {
            List<ShadowConfig> configs = List.of(
                    buildConfig(1L, "project/pkg1", "project/pkg1-v2", 50, true),
                    buildConfig(2L, "project/pkg2", "project/pkg2-v2", 100, true),
                    buildConfig(3L, "project/pkg3", "project/pkg3-v2", 10, false)
            );
            when(shadowConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(configs);

            List<ShadowConfig> result = service.listAll();

            assertEquals(3, result.size());
        }
    }

    // ===== 场景 4: 切换状态 =====

    @Nested
    @DisplayName("切换启停状态")
    class ToggleConfig {

        @Test
        @DisplayName("Given 配置 enabled=true, When toggle(id, false), Then enabled=false")
        void toggle_disables() {
            ShadowConfig config = buildConfig(1L, "project/pkg1", "project/pkg1-v2", 50, true);
            when(shadowConfigMapper.selectById(1L)).thenReturn(config);

            service.toggle(1L, false);

            assertFalse(config.getEnabled());
            verify(shadowConfigMapper).updateById(config);
        }
    }

    // ===== 场景 5: 创建配置 =====

    @Nested
    @DisplayName("创建陪跑配置")
    class CreateConfig {

        @Test
        @DisplayName("Given 有效的 ShadowConfig, When create, Then 插入数据库并返回带 id 的实体")
        void create_insertsAndReturns() {
            ShadowConfig config = buildConfig(null, "project/pkg1", "project/pkg1-v2", 50, true);
            when(shadowConfigMapper.insert(any(ShadowConfig.class))).thenAnswer(invocation -> {
                ShadowConfig arg = invocation.getArgument(0);
                arg.setId(1L);
                return 1;
            });

            ShadowConfig created = service.create(config);

            assertNotNull(created.getId());
            assertEquals(1L, created.getId());
            verify(shadowConfigMapper).insert(config);
        }
    }

    // ===== 场景 6: 删除配置 =====

    @Nested
    @DisplayName("删除陪跑配置")
    class DeleteConfig {

        @Test
        @DisplayName("Given 配置 id=1 存在, When delete(1), Then mapper.delete 被调用")
        void delete_callsMapper() {
            when(shadowConfigMapper.deleteById(1L)).thenReturn(1);

            service.delete(1L);

            verify(shadowConfigMapper).deleteById(1L);
        }
    }

    // ===== 额外: null/禁用 配置 =====

    @Nested
    @DisplayName("边界条件")
    class EdgeCases {

        @Test
        @DisplayName("Given null config, When shouldExecuteShadow, Then 返回 false")
        void nullConfig_returnsFalse() {
            assertFalse(service.shouldExecuteShadow(null));
        }

        @Test
        @DisplayName("Given disabled config, When shouldExecuteShadow, Then 返回 false")
        void disabledConfig_returnsFalse() {
            ShadowConfig config = buildConfig(1L, "project/pkg1", "project/pkg1-v2", 50, false);

            assertFalse(service.shouldExecuteShadow(config));
        }

        @Test
        @DisplayName("Given null sampleRate, When shouldExecuteShadow, Then 返回 false")
        void nullSampleRate_returnsFalse() {
            ShadowConfig config = buildConfig(1L, "project/pkg1", "project/pkg1-v2", 0, true);
            config.setSampleRate(null);

            assertFalse(service.shouldExecuteShadow(config));
        }
    }
}
