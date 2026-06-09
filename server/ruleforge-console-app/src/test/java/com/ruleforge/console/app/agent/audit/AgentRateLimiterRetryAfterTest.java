package com.ruleforge.console.app.agent.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentRateLimiter V5.22.3 — retryAfterSeconds 测试
 *
 * 限流命中时,异常带 retryAfterSeconds 字段 = 滑动窗口最早一调用距 now 的秒数
 */
@DisplayName("AgentRateLimiter - retryAfterSeconds")
class AgentRateLimiterRetryAfterTest {

    private AgentRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AgentRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxPerHour", 2);
        limiter.reset();
    }

    @Test
    @DisplayName("Given 用户连续调 2 次 When 调第 3 次 Then 异常带 retryAfterSeconds > 0")
    void shouldIncludeRetryAfterSeconds() {
        // 调满窗口
        limiter.check("BA1", null);
        limiter.check("BA1", null);

        // 第 3 次 — 应该命中限流
        assertThatThrownBy(() -> limiter.check("BA1", null))
                .isInstanceOf(AgentRateLimiter.RateLimitExceededException.class)
                .satisfies(e -> {
                    AgentRateLimiter.RateLimitExceededException ex = (AgentRateLimiter.RateLimitExceededException) e;
                    long retryAfter = ex.getRetryAfterSeconds();
                    assertThat(retryAfter).isBetween(1L, 3600L);
                });
    }

    @Test
    @DisplayName("Given sessionId 限流命中 When check Then 异常也带 retryAfterSeconds")
    void shouldIncludeRetryAfterForSession() {
        limiter.check(null, "sess_1");
        limiter.check(null, "sess_1");
        assertThatThrownBy(() -> limiter.check(null, "sess_1"))
                .isInstanceOf(AgentRateLimiter.RateLimitExceededException.class)
                .satisfies(e -> {
                    AgentRateLimiter.RateLimitExceededException ex = (AgentRateLimiter.RateLimitExceededException) e;
                    assertThat(ex.getRetryAfterSeconds()).isBetween(1L, 3600L);
                });
    }
}
