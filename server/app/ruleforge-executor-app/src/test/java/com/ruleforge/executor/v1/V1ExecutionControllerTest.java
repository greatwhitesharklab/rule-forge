package com.ruleforge.executor.v1;

import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.RuleAssetIO;
import com.ruleforge.v1.exec.V1FlowRunner;
import com.ruleforge.v1.exec.V1PublishedBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Feature: V7.7 executor V1 运行时 — 生产执行已发布决策流
 *
 * <p>背景:V7.6 console 已能发布决策流(冻结闭包 bundle 入 rf_v1_publish + git tag)。
 * V7.7 executor 加 V1 运行时:V1ResourceProvider 拉 console bundle → V1FlowRunner 跑。
 * 这是 V1 的生产执行路径(对应老 urule executor 拉 .rp 知识包)。
 *
 * <p>链:POST /v1/exec?flow= → V1ResourceProvider.fetchBundle(flow) → V1FlowRunner.execute(bundle)。
 */
@DisplayName("V1ExecutionController — 生产执行已发布流")
class V1ExecutionControllerTest {

    /** 最小可执行 bundle:Start → Decision(endEvent),默认输出 review。 */
    private static final String FLOW_JSON = "{"
            + "\"version\":\"1.0\",\"id\":\"a1\",\"name\":\"d\","
            + "\"flow\":{\"id\":\"f1\",\"name\":\"F\",\"version\":\"1.0\",\"flowElements\":["
            + "{\"type\":\"startEvent\",\"id\":\"s\",\"name\":\"S\"},"
            + "{\"type\":\"endEvent\",\"id\":\"dec\",\"name\":\"D\",\"implementation\":\"Decision:dec\"},"
            + "{\"type\":\"sequenceFlow\",\"id\":\"e\",\"sourceRef\":\"s\",\"targetRef\":\"dec\"}"
            + "]},"
            + "\"nodes\":{\"dec\":{\"id\":\"dec\",\"type\":\"Decision\",\"name\":\"D\","
            + "\"outputs\":[\"approve\",\"review\",\"reject\"],\"decisionField\":\"decision\",\"defaultOutput\":\"review\"}},"
            + "\"schema\":{\"name\":\"Loan\",\"fields\":[]}"
            + "}";

    private V1PublishedBundle sampleBundle() throws Exception {
        RuleAsset asset = RuleAssetIO.mapper().readValue(FLOW_JSON, RuleAsset.class);
        return new V1PublishedBundle(asset, null, Collections.emptyMap());
    }

    @Nested
    @DisplayName("exec(flow, fact)")
    class Exec {

        @Test
        @DisplayName("GIVEN 已发布 bundle WHEN exec(空 fact)THEN decision = Decision 默认输出(review)")
        void runsPublishedBundle() throws Exception {
            V1ResourceProvider provider = mock(V1ResourceProvider.class);
            when(provider.fetchBundle("/p/loan.v1flow.json")).thenReturn(sampleBundle());
            V1ExecutionController controller = new V1ExecutionController(provider);

            V1FlowRunner.FlowResult result = controller.exec("/p/loan.v1flow.json", new HashMap<>());

            // 空 fact → Decision 走 defaultOutput(review)
            assertThat(result).isNotNull();
            assertThat(result.decision).isEqualTo("review");
            assertThat(result.fact).isNotNull();
        }

        @Test
        @DisplayName("GIVEN bundle = null(未发布)WHEN exec THEN 抛 IllegalArgumentException(提示先发布)")
        void unpublishedThrows() {
            V1ResourceProvider provider = mock(V1ResourceProvider.class);
            when(provider.fetchBundle(anyFlow())).thenReturn(null);
            V1ExecutionController controller = new V1ExecutionController(provider);

            assertThatThrownBy(() -> controller.exec("/p/x.v1flow.json", new HashMap<>()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("未发布");
        }

        @Test
        @DisplayName("GIVEN fact 带 decision=approve WHEN exec THEN decision = approve")
        void factDecisionOverridesDefault() throws Exception {
            V1ResourceProvider provider = mock(V1ResourceProvider.class);
            when(provider.fetchBundle(anyFlow())).thenReturn(sampleBundle());
            V1ExecutionController controller = new V1ExecutionController(provider);

            Map<String, Object> fact = new HashMap<>();
            fact.put("decision", "approve");

            V1FlowRunner.FlowResult result = controller.exec("/p/loan.v1flow.json", fact);

            assertThat(result.decision).isEqualTo("approve");
        }
    }

    private static String anyFlow() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
