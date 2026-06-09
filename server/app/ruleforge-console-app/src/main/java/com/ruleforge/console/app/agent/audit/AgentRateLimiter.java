package com.ruleforge.console.app.agent.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 工具调用限流器 (V5.22.2)
 *
 * <p>每个 user + session 组合,过去 1 小时最多 100 次工具调用(滑动窗口)。
 * <p>user 单独也限(防止同一 user 走多 session 绕开)。
 * <p>超出抛 {@link RateLimitExceededException}。
 *
 * <p>内存存储 — 重启会清空(对短期滥用足够;长期滥用交给 nd_agent_audit 离线分析)。
 */
@Slf4j
@Component
public class AgentRateLimiter {

    /** 默认 100 calls / hour(用户原话) */
    @Value("${ruleforge.agent.rate-limit.max-per-hour:100}")
    private int maxPerHour;

    /** key = userId|sessionId(只 user 也算,只 session 也算) */
    private final ConcurrentHashMap<String, Deque<Instant>> userWindow = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Instant>> sessionWindow = new ConcurrentHashMap<>();

    /**
     * 检查并记录一次调用。
     *
     * @throws RateLimitExceededException 超出上限时
     */
    public void check(String userId, String sessionId) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(1, ChronoUnit.HOURS);

        if (userId != null && !userId.isEmpty()) {
            Deque<Instant> w = userWindow.computeIfAbsent(userId, k -> new ArrayDeque<>());
            if (!tryAcquire(w, now, cutoff)) {
                long retryAfter = secondsUntilOldestExits(w, now);
                throw new RateLimitExceededException(
                        "用户 " + userId + " 超过每小时 " + maxPerHour + " 次调用上限",
                        retryAfter);
            }
        }
        if (sessionId != null && !sessionId.isEmpty()) {
            Deque<Instant> w = sessionWindow.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
            if (!tryAcquire(w, now, cutoff)) {
                long retryAfter = secondsUntilOldestExits(w, now);
                throw new RateLimitExceededException(
                        "会话 " + sessionId + " 超过每小时 " + maxPerHour + " 次调用上限",
                        retryAfter);
            }
        }
    }

    /**
     * 算滑动窗口里"最早一次调用"还需要几秒才滑出 1 小时窗口。
     * 给客户端一个明确的 retry-after,而不是干瞪眼等 60 分钟。
     */
    private long secondsUntilOldestExits(Deque<Instant> window, Instant now) {
        synchronized (window) {
            if (window.isEmpty()) return 1;
            Instant oldest = window.peekFirst();
            Instant exitTime = oldest.plus(1, ChronoUnit.HOURS);
            long secs = ChronoUnit.SECONDS.between(now, exitTime);
            return Math.max(1, secs);  // 至少 1 秒,前端好处理
        }
    }

    /** 获取当前用户最近一小时调用数(只读) */
    public int currentHourCount(String userId) {
        Deque<Instant> ts = userWindow.get(userId);
        if (ts == null) return 0;
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        synchronized (ts) {
            return (int) ts.stream().filter(t -> t.isAfter(cutoff)).count();
        }
    }

    private boolean tryAcquire(Deque<Instant> window, Instant now, Instant cutoff) {
        synchronized (window) {
            // 滑动窗口:丢掉超过 1 小时的旧时间戳
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= maxPerHour) {
                log.warn("[RateLimit] 上限命中 size={} max={}", window.size(), maxPerHour);
                return false;
            }
            window.addLast(now);
            return true;
        }
    }

    /** 测试 / 管理用:清空所有窗口 */
    public void reset() {
        userWindow.clear();
        sessionWindow.clear();
    }

    public int getMaxPerHour() {
        return maxPerHour;
    }

    /** 限流异常(V5.22.3 — 带 retryAfterSeconds 字段) */
    public static class RateLimitExceededException extends RuntimeException {
        private final long retryAfterSeconds;
        public RateLimitExceededException(String msg, long retryAfterSeconds) {
            super(msg);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}
