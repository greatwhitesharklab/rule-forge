package com.ruleforge.rete.perf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.builder.ResourceLibraryBuilder;
import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V5.49.2 — DRL-only rebuild perf regression(配 {@code perf/baseline.json})。
 *
 * <p>目的:在 facade 拆分(V5.48)后,确认 {@link RulesRebuilder#rebuildRules}
 * 走 facade + DrlRuleRebuilder fallback 路径的 throughput 没掉。
 *
 * <p>workload:
 * <ul>
 *   <li>1000 个 minimal {@link Rule}(null LHS,空 RHS — 走 DrlRuleRebuilder
 *       fallback 但全是 null-guard skip,代表"DRL only rebuild"上限)</li>
 *   <li>空 libraries + mock libBuilder 返空 ResourceLibrary(null-guard 早退)</li>
 *   <li>5 warmup + 50 measurement(per bench 行业标准)</li>
 * </ul>
 *
 * <p>对比 {@code perf/baseline.json} sample
 * {@code DrlRebuildPerfRegressionTest_drl_1000_rules_ms}:p50=4500 / p95=5200 /
 * failMultiplier=1.5。p50 超 baseline×1.5 才 fail(留 50% CI 抖动缓冲)。
 *
 * <p>{@code @Tag("perf")} — 日常 P0 CI 排除,只跑 {@code perf-bench-nightly.yml}
 * cron。
 */
@Tag("perf")
@DisplayName("V5.49.2 — DRL rebuild perf regression(1000 规则,facade 拆分后)")
class DrlRebuildPerfRegressionTest {

    /** sample key in baseline.json — 跟 V5.46 实测对齐。 */
    private static final String SAMPLE_NAME = "DrlRebuildPerfRegressionTest_drl_1000_rules_ms";

    /** 1000 minimal Rule 走 RulesRebuilder.rebuildRules。 */
    private static final int N = 1000;

    @Test
    @DisplayName("1000 规则 DRL rebuild p50 应 < baseline p50 × failMultiplier")
    void drlOnlyRebuild1000Rules() throws Exception {
        // 1) load baseline
        double[] bounds = loadBaseline(SAMPLE_NAME);
        double p50 = bounds[0];
        double failMultiplier = bounds[1];
        double threshold = p50 * failMultiplier;
        System.out.printf("[V5.49.2 baseline] sample=%s p50=%.2fms failMultiplier=%.2f threshold=%.2fms%n",
            SAMPLE_NAME, p50, failMultiplier, threshold);

        // 2) 1000 minimal rules(null LHS + 空 RHS — 走 DrlRuleRebuilder fallback
        //    但 LHS/RHS 全 null-guard skip,代表"DRL only rebuild"上限)
        List<Rule> rules = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            Rule r = new Rule();
            r.setName("perf-rule-" + i);
            Rhs rhs = new Rhs();
            rhs.setActions(Collections.emptyList());
            r.setRhs(rhs);
            rules.add(r);
        }

        // 3) RulesRebuilder + mock libBuilder
        RulesRebuilder rebuilder = new RulesRebuilder();
        ResourceLibraryBuilder libBuilder = mock(ResourceLibraryBuilder.class);
        when(libBuilder.buildResourceLibrary(Collections.<Library>emptyList(), false))
            .thenReturn(new ResourceLibrary());
        Field f = RulesRebuilder.class.getDeclaredField("resourceLibraryBuilder");
        f.setAccessible(true);
        f.set(rebuilder, libBuilder);

        // 4) 5 warmup
        for (int i = 0; i < 5; i++) {
            rebuilder.rebuildRules(Collections.<Library>emptyList(), rules);
        }

        // 5) 50 measurement
        long[] nanos = new long[50];
        for (int i = 0; i < 50; i++) {
            long t0 = System.nanoTime();
            rebuilder.rebuildRules(Collections.<Library>emptyList(), rules);
            nanos[i] = System.nanoTime() - t0;
        }

        // 6) stats
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        double p50Ms = sorted[sorted.length / 2] / 1e6;
        double p95Ms = sorted[(int) Math.round(0.95 * (sorted.length - 1))] / 1e6;
        double maxMs = sorted[sorted.length - 1] / 1e6;
        double meanMs = Arrays.stream(nanos).sum() / 1e6 / nanos.length;
        System.out.printf("[V5.49.2 actual] n=%d mean=%.2fms p50=%.2fms p95=%.2fms max=%.2fms%n",
            nanos.length, meanMs, p50Ms, p95Ms, maxMs);

        // 7) assert p50 ≤ threshold
        if (p50Ms > threshold) {
            fail(String.format(
                "PERF REGRESSION: %s p50=%.2fms 超 baseline p50=%.2fms × %.2f = %.2fms%n"
                    + "→ facade 拆分或 RulesRebuilder 改动可能影响 throughput,需排查。",
                SAMPLE_NAME, p50Ms, p50, failMultiplier, threshold));
        }
        assertTrue(p50Ms <= threshold, "p50 已在 threshold 内");
    }

    /**
     * 读 baseline.json sample 返 {@code [p50, failMultiplier]}。
     */
    private static double[] loadBaseline(String sampleName) {
        try (InputStream in = DrlRebuildPerfRegressionTest.class.getResourceAsStream(
                "/perf/baseline.json")) {
            if (in == null) {
                throw new IllegalStateException("/perf/baseline.json 找不到 — "
                    + "检查 src/test/resources 拷贝");
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(in);
            JsonNode samples = root.get("samples");
            if (samples == null || !samples.isArray()) {
                throw new IllegalStateException("baseline.json 缺 samples 数组");
            }
            for (JsonNode s : samples) {
                if (sampleName.equals(s.get("name").asText())) {
                    double p50 = s.get("p50").asDouble();
                    double mult = s.get("failMultiplier").asDouble();
                    return new double[] {p50, mult};
                }
            }
            throw new IllegalStateException("baseline.json 缺 sample: " + sampleName);
        } catch (Exception e) {
            throw new RuntimeException("loadBaseline 失败", e);
        }
    }
}
