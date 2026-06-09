package com.ruleforge.console.app.agent.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentRateLimiter 单元测试 (V5.22.2)
 *
 * 不走 Spring,直接 new。配置 maxPerHour = 100(默认)。
 * 测:首 N 次通过 / 第 101 次抛 / 限流错误信息 / 跨 user 独立 / reset
 */
@DisplayName("AgentRateLimiter - 100 calls / hour per user + per session")
class AgentRateLimiterTest {

    private AgentRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AgentRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxPerHour", 100);
    }

    @Nested
    @DisplayName("Scenario: 滑动窗口")
    class SlidingWindow {

        @Test
        @DisplayName("Given userId 调 100 次 When 第 101 次 Then 抛 RateLimitExceededException")
        void shouldBlockAfter100() {
            for (int i = 0; i < 100; i++) {
                limiter.check("user1", "sess1");
            }
            assertThatThrownBy(() -> limiter.check("user1", "sess1"))
                    .isInstanceOf(AgentRateLimiter.RateLimitExceededException.class)
                    .hasMessageContaining("user1")
                    .hasMessageContaining("100");
        }

        @Test
        @DisplayName("Given user1 满限 When user2 调 Then 仍能过(独立窗口)")
        void shouldIsolateUsers() {
            for (int i = 0; i < 100; i++) {
                limiter.check("user1", null);
            }
            // user2 第一次
            limiter.check("user2", null);
            assertThat(limiter.currentHourCount("user1")).isEqualTo(100);
            assertThat(limiter.currentHourCount("user2")).isEqualTo(1);
        }

        @Test
        @DisplayName("Given user/session 都有窗口 When 各自满限 Then 各自抛")
        void shouldLimitOnBothUserAndSession() {
            // user1 100 次(同 session)
            for (int i = 0; i < 100; i++) {
                limiter.check("user1", "sess1");
            }
            // user1 换 session 仍被 user 限流挡
            assertThatThrownBy(() -> limiter.check("user1", "sess2"))
                    .isInstanceOf(AgentRateLimiter.RateLimitExceededException.class)
                    .hasMessageContaining("user1");
        }

        @Test
        @DisplayName("Given userId=null When 调 Then 不抛")
        void shouldAllowAnonymous() {
            // null/空 userId 跳过 user 限流,只走 session 限流
            for (int i = 0; i < 200; i++) {
                limiter.check(null, null);
            }
            // 不抛
        }

        @Test
        @DisplayName("Given reset When Then 窗口清空,能再调 100 次")
        void shouldReset() {
            for (int i = 0; i < 100; i++) {
                limiter.check("u", null);
            }
            assertThatThrownBy(() -> limiter.check("u", null)).isInstanceOf(AgentRateLimiter.RateLimitExceededException.class);

            limiter.reset();
            // 重新可以调 100 次
            for (int i = 0; i < 100; i++) {
                limiter.check("u", null);
            }
        }
    }

    @Test
    @DisplayName("currentHourCount 反映当前窗口大小")
    void shouldReflectCurrentCount() {
        assertThat(limiter.currentHourCount("u1")).isEqualTo(0);
        for (int i = 0; i < 5; i++) {
            limiter.check("u1", null);
        }
        assertThat(limiter.currentHourCount("u1")).isEqualTo(5);
    }

    @Test
    @DisplayName("maxPerHour getter 返配置值")
    void shouldExposeMaxPerHour() {
        assertThat(limiter.getMaxPerHour()).isEqualTo(100);
    }
}
