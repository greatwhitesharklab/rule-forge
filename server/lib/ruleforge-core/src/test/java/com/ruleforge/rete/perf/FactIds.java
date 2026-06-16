package com.ruleforge.rete.perf;

import java.util.concurrent.atomic.AtomicLong;

/**
 * V5.91 — zero-allocation, zero-random fact-name 生成 helper 给 perf tests。
 *
 * <p>替换 {@code UUID.randomUUID().toString()}。后者占 V5.90 HotPathBenchTest
 * 30s JFR top hot method 28%({@code String.hashCode} 313 + {@code SecureRandom}
 * 链 230+ sample = 543 leaf sample,见
 * {@code target/v590.jfr})。UUID v4 内部走
 * {@code MessageDigest} + {@code SecureRandom.updateState} +
 * {@code String.hashCode}({@code toString} 缓存 hash),per-fact 全部付这个
 * 成本。
 *
 * <p>{@link AtomicLong} 计数器格式化 {@code "p-1"}/{@code "a-2"}/... 稳定 +
 * 决定性 + 极小分配,vs UUID 的 MessageDigest + SecureRandom + hash 链。
 * Per-fact UUID cost 全部消除。配合 bench 显式
 * {@code setDebug(false)}(V5.90),HotPath per-fact 进一步从 V5.90 0.36us
 * → V5.91 预期 0.25-0.30us(-15~30%)。
 *
 * <p>放在 {@code rete/perf/} 跟 V5.85 立的 {@code EngineContextWirer}
 * (V5.81 rete/test/) 模式一致 — 标 V5.x perf test infrastructure 的 helper
 * 都放 rete 子包。
 *
 * @since 5.91
 */
public final class FactIds {

    private static final AtomicLong COUNTER = new AtomicLong();

    private FactIds() {}

    /**
     * 返回 {@code "{prefix}-{n}"},{@code n} 全局原子递增(1-based)。
     *
     * <p>无 SecureRandom、无 MessageDigest、无 hash 缓存,纯 atomic counter +
     * StringBuilder-style concat。
     */
    public static String next(String prefix) {
        return prefix + "-" + COUNTER.incrementAndGet();
    }

    /** 默认 prefix 是 {@code "f"},匹配 {@code next("f")}。 */
    public static String next() {
        return next("f");
    }

    /**
     * 把 counter 归零。仅 test 间用,production 永不调。
     */
    public static void reset() {
        COUNTER.set(0);
    }
}
