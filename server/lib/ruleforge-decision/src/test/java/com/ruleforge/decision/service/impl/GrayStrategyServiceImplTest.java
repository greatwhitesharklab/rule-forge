package com.ruleforge.decision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.decision.dto.GrayResolution;
import com.ruleforge.decision.entity.GrayStrategy;
import com.ruleforge.decision.mapper.rf.GrayStrategyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * GrayStrategyServiceImpl 单元测试
 *
 * BDD 行为场景:
 *
 * 1. PERCENT_USER: userId.hashCode() % 100 < grayPercent → 命中
 * 2. PERCENT_RANDOM: random % 100 < grayPercent → 命中（概率性，验证范围）
 * 3. WHITELIST: userId 在白名单中 → 命中
 * 4. 策略优先级: WHITELIST > PERCENT_USER > PERCENT_RANDOM
 * 5. 无策略 / 策略停用 → 返回 null 或 baseline
 */
@ExtendWith(MockitoExtension.class)
class GrayStrategyServiceImplTest {

    @Mock
    private GrayStrategyMapper grayStrategyMapper;

    private GrayStrategyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GrayStrategyServiceImpl(grayStrategyMapper);
    }

    private GrayStrategy buildStrategy(String type, int percent, String whitelist,
                                        String targetTag, String baselineTag) {
        GrayStrategy s = new GrayStrategy();
        s.setId(1L);
        s.setProjectId(1L);
        s.setPackageId("pkg1");
        s.setStrategyType(type);
        s.setGrayPercent(percent);
        s.setWhitelist(whitelist);
        s.setTargetGitTag(targetTag);
        s.setBaselineGitTag(baselineTag);
        s.setEnabled(true);
        return s;
    }

    private void mockStrategies(List<GrayStrategy> strategies) {
        when(grayStrategyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(strategies);
    }

    // ===== 场景 5: 无策略 =====

    @Nested
    @DisplayName("无灰度策略")
    class NoStrategy {

        @Test
        @DisplayName("Given 无灰度策略, When resolveVersion, Then 返回 null")
        void noStrategy_returnsNull() {
            mockStrategies(Collections.emptyList());

            GrayResolution result = service.resolveVersion("project/pkg1", "user1");

            assertNull(result);
        }
    }

    // ===== 场景 1: PERCENT_USER =====

    @Nested
    @DisplayName("PERCENT_USER 策略")
    class PercentUser {

        @Test
        @DisplayName("Given PERCENT_USER 100%, When 任意 userId, Then 命中灰度")
        void percent100_alwaysHit() {
            GrayStrategy strategy = buildStrategy("PERCENT_USER", 100, null, "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", "anyUser");

            assertNotNull(result);
            assertTrue(result.isGrayHit());
            assertEquals("v2.0", result.getGitTag());
            assertEquals(1L, result.getStrategyId());
        }

        @Test
        @DisplayName("Given PERCENT_USER 0%, When 任意 userId, Then 未命中灰度")
        void percent0_neverHit() {
            GrayStrategy strategy = buildStrategy("PERCENT_USER", 0, null, "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", "anyUser");

            assertNotNull(result);
            assertFalse(result.isGrayHit());
            assertEquals("v1.0", result.getGitTag());
        }

        @Test
        @DisplayName("Given PERCENT_USER 50%, Then 同一用户多次调用结果一致（稳定哈希）")
        void percent50_stableHash() {
            GrayStrategy strategy = buildStrategy("PERCENT_USER", 50, null, "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            String userId = "stable_user_123";
            GrayResolution first = service.resolveVersion("project/pkg1", userId);
            GrayResolution second = service.resolveVersion("project/pkg1", userId);

            assertEquals(first.isGrayHit(), second.isGrayHit(),
                    "同一用户多次调用结果应一致");
        }

        @Test
        @DisplayName("Given PERCENT_USER null userId, Then 未命中灰度")
        void percentUser_nullUserId_returnsBaseline() {
            GrayStrategy strategy = buildStrategy("PERCENT_USER", 50, null, "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", null);

            assertFalse(result.isGrayHit());
            assertEquals("v1.0", result.getGitTag());
        }
    }

    // ===== 场景 2: PERCENT_RANDOM =====

    @Nested
    @DisplayName("PERCENT_RANDOM 策略")
    class PercentRandom {

        @Test
        @DisplayName("Given PERCENT_RANDOM 100%, When 调用, Then 必定命中")
        void random100_alwaysHit() {
            GrayStrategy strategy = buildStrategy("PERCENT_RANDOM", 100, null, "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", "user1");

            assertTrue(result.isGrayHit());
            assertEquals("v2.0", result.getGitTag());
        }

        @Test
        @DisplayName("Given PERCENT_RANDOM 0%, When 调用, Then 必定未命中")
        void random0_neverHit() {
            GrayStrategy strategy = buildStrategy("PERCENT_RANDOM", 0, null, "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", "user1");

            assertFalse(result.isGrayHit());
            assertEquals("v1.0", result.getGitTag());
        }

        @Test
        @DisplayName("Given PERCENT_RANDOM 50%, Then 多次调用应有一定比例命中")
        void random50_someHitSomeMiss() {
            GrayStrategy strategy = buildStrategy("PERCENT_RANDOM", 50, null, "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            int hits = 0;
            int total = 1000;
            for (int i = 0; i < total; i++) {
                GrayResolution result = service.resolveVersion("project/pkg1", "user" + i);
                if (result.isGrayHit()) hits++;
            }

            // 随机策略，1000 次调用应有 ~500 次命中，允许 ±10% 误差
            assertTrue(hits > total * 0.35 && hits < total * 0.65,
                    "50% 灰度应有约一半命中, 实际命中: " + hits + "/" + total);
        }
    }

    // ===== 场景 3: WHITELIST =====

    @Nested
    @DisplayName("WHITELIST 策略")
    class Whitelist {

        @Test
        @DisplayName("Given WHITELIST [userA, userB], When userId=userA, Then 命中灰度")
        void whitelist_userInList_hit() {
            GrayStrategy strategy = buildStrategy("WHITELIST", 0, "userA,userB", "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", "userA");

            assertTrue(result.isGrayHit());
            assertEquals("v2.0", result.getGitTag());
        }

        @Test
        @DisplayName("Given WHITELIST [userA, userB], When userId=userC, Then 未命中灰度")
        void whitelist_userNotInList_miss() {
            GrayStrategy strategy = buildStrategy("WHITELIST", 0, "userA,userB", "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", "userC");

            assertFalse(result.isGrayHit());
            assertEquals("v1.0", result.getGitTag());
        }

        @Test
        @DisplayName("Given WHITELIST null userId, Then 未命中灰度")
        void whitelist_nullUserId_miss() {
            GrayStrategy strategy = buildStrategy("WHITELIST", 0, "userA,userB", "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", null);

            assertFalse(result.isGrayHit());
        }

        @Test
        @DisplayName("Given WHITELIST 空白名单, Then 未命中灰度")
        void whitelist_emptyWhitelist_miss() {
            GrayStrategy strategy = buildStrategy("WHITELIST", 0, "", "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", "userA");

            assertFalse(result.isGrayHit());
        }

        @Test
        @DisplayName("Given WHITELIST 含空格的名单 'userA, userB ', When userId=userB, Then 命中（trim）")
        void whitelist_whitespaceTrimmed() {
            GrayStrategy strategy = buildStrategy("WHITELIST", 0, "userA, userB , userC", "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", "userB");

            assertTrue(result.isGrayHit());
        }
    }

    // ===== 场景 4: 策略优先级 =====

    @Nested
    @DisplayName("多策略优先级")
    class Priority {

        @Test
        @DisplayName("Given WHITELIST + PERCENT_USER, When userId 在白名单, Then 命中 WHITELIST 策略")
        void whitelistTakesPriority() {
            GrayStrategy whitelist = buildStrategy("WHITELIST", 0, "userA", "v3.0", "v1.0");
            whitelist.setId(2L);
            GrayStrategy percent = buildStrategy("PERCENT_USER", 100, null, "v2.0", "v1.0");
            percent.setId(1L);
            // PERCENT_USER 100% 会命中所有人，但 WHITELIST 在后面遍历时先匹配
            mockStrategies(List.of(percent, whitelist));

            GrayResolution result = service.resolveVersion("project/pkg1", "userA");

            assertTrue(result.isGrayHit());
            assertEquals(2L, result.getStrategyId());
        }

        @Test
        @DisplayName("Given 策略 enabled=false, When resolveVersion, Then 该策略被跳过")
        void disabledStrategy_skipped() {
            GrayStrategy disabled = buildStrategy("WHITELIST", 0, "userA", "v2.0", "v1.0");
            disabled.setEnabled(false);
            mockStrategies(List.of(disabled));

            GrayResolution result = service.resolveVersion("project/pkg1", "userA");

            assertFalse(result.isGrayHit());
        }
    }

    // ===== 场景: packagePath 解析 =====

    @Nested
    @DisplayName("packagePath 解析")
    class PackagePath {

        @Test
        @DisplayName("Given packagePath 'project/pkg1', When 查询策略, Then 用 packageId='pkg1' 查询")
        void extractsPackageId_fromPath() {
            GrayStrategy strategy = buildStrategy("WHITELIST", 0, "userA", "v2.0", "v1.0");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("project/pkg1", "userA");

            assertTrue(result.isGrayHit());
        }

        @Test
        @DisplayName("Given packagePath 无斜线 'pkg1', When 查询策略, Then 用整个字符串作为 packageId")
        void noSlash_usesEntirePath() {
            GrayStrategy strategy = buildStrategy("WHITELIST", 0, "userA", "v2.0", "v1.0");
            strategy.setPackageId("pkg1");
            mockStrategies(List.of(strategy));

            GrayResolution result = service.resolveVersion("pkg1", "userA");

            assertTrue(result.isGrayHit());
        }
    }
}
