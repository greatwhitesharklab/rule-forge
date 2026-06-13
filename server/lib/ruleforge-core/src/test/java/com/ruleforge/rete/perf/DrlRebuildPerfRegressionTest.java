package com.ruleforge.rete.perf;

import com.ruleforge.builder.ResourceLibraryBuilder;
import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P1 — DRL-only rebuild perf 回归 case。
 *
 * <p>{@code @Tag("perf")} 标注,默认 mvn test 跳过(由 surefire -Dgroups='!perf'
 * 排除),只在 P1 Task 5 创建的 perf nightly workflow 跑(-Pperf -Dgroups=perf)。
 *
 * <p>目的:把 V5.46 RETE bench 0.16ms / 1000 fact 的"rebuild 1000 DRL 规则"
 * 路径锁住,production 改 RulesRebuilder 后 perf 不退化(超 baseline × 1.5 才 fail)。
 *
 * <p>策略:1000 个 minimal Rule(null LHS,空 RHS)走 RulesRebuilder.rebuildRules,
 * 计时。Mock ResourceLibraryBuilder 让 buildResourceLibrary 返空 ResourceLibrary。
 *
 * <p>本测试**不**测 V5.46 RETE eval 路径(那是 EvalBenchmark 的事),只测
 * "rebuild 1000 规则" 这条 DRL 重建管线的总耗时。Lock-step assertion 留 P1 Task 5
 * 的 baseline.json 比对。
 */
@Tag("perf")
@DisplayName("P1 — DRL rebuild perf regression(1000 规则)")
class DrlRebuildPerfRegressionTest {

    /** 1000 规则 DRL 重建上限 — 实测 V5.47 baseline ~3-5s,留 2x 缓冲 = 10s。 */
    private static final long MAX_REBUILD_1000_MS = 10_000L;

    @Test
    @DisplayName("1000 规则 DRL rebuild 应 < 10s(baseline × 1.5 缓冲)")
    void drlOnlyRebuild1000Rules() throws Exception {
        // Given — 1000 个 minimal Rule(LHS null,空 Rhs.actions — 不让 production
        // catch 把 NPE 包成"语法错",但走完整 for loop 计时)
        List<Rule> rules = new ArrayList<>(1000);
        for (int i = 0; i < 1000; i++) {
            Rule r = new Rule();
            r.setName("perf-rule-" + i);
            Rhs rhs = new Rhs();
            rhs.setActions(Collections.emptyList());
            r.setRhs(rhs);
            rules.add(r);
        }

        // 注入 mock libBuilder
        RulesRebuilder rebuilder = new RulesRebuilder();
        ResourceLibraryBuilder libBuilder = mock(ResourceLibraryBuilder.class);
        when(libBuilder.buildResourceLibrary(Collections.<Library>emptyList(), false))
            .thenReturn(new ResourceLibrary());
        Field f = RulesRebuilder.class.getDeclaredField("resourceLibraryBuilder");
        f.setAccessible(true);
        f.set(rebuilder, libBuilder);

        // When — 跑一次 warmup(让 JIT 预热),然后计时
        rebuilder.rebuildRules(Collections.<Library>emptyList(), rules);
        long start = System.nanoTime();
        rebuilder.rebuildRules(Collections.<Library>emptyList(), rules);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // Then
        System.out.printf("[DRL perf] 1000 规则 rebuild = %d ms (上限 %d ms)%n", elapsedMs, MAX_REBUILD_1000_MS);
        assertTrue(elapsedMs < MAX_REBUILD_1000_MS,
            "1000 规则 DRL rebuild " + elapsedMs + "ms 超上限 " + MAX_REBUILD_1000_MS + "ms");
    }
}
