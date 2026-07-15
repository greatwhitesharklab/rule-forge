package com.ruleforge.console.batchtest.impl;

import com.ruleforge.console.batchtest.SubjectExecutionContext;
import com.ruleforge.console.batchtest.SubjectResult;
import com.ruleforge.v1.ast.RuleAsset;
import com.ruleforge.v1.ast.RuleAssetIO;
import com.ruleforge.v1.exec.V1PublishedBundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.23 — V1 决策流批测 subject BDD。
 *
 * <p>测 {@link V1BatchTestSubject}:给定一个已 resolve 的 V1 bundle(DecisionTable-only flow,
 * 不进 RETE,无需 EngineContext 装配)+ 一行输入 fact,执行后 output 应含正确的 decision + fact。
 *
 * <p>fixture(pricing_flow.json):score>=80 → approve,catch-all → reject。
 * 三个场景:高分 approve / 低分 reject / 缺 bundle 失败。
 */
@DisplayName("V7.23 V1BatchTestSubject — 逐行跑 V1FlowRunner")
class V1BatchTestSubjectTest {

    private static V1PublishedBundle bundle;

    @BeforeAll
    static void setup() throws Exception {
        // DecisionTable-only flow(走 CEL,不进 RETE,console-app 单测无需 EngineContext)
        try (InputStream in = V1BatchTestSubjectTest.class.getResourceAsStream(
                "/com/ruleforge/v1/ast/pricing_flow.json")) {
            assertThat(in).as("pricing_flow.json fixture 存在").isNotNull();
            RuleAsset asset = RuleAssetIO.read(in);
            bundle = new V1PublishedBundle(asset, null, java.util.Collections.emptyMap());
        }
    }

    private SubjectResult run(Map<String, Object> inputFact) {
        V1BatchTestSubject subject = new V1BatchTestSubject();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(V1BatchTestSubject.PARAM_BUNDLE, bundle);
        params.put(V1BatchTestSubject.PARAM_FLOW_PATH, "/p/V1决策流/pricing.v1flow.json");
        SubjectExecutionContext ctx = new SubjectExecutionContext(1L, 100L, inputFact, params);
        return subject.execute(ctx);
    }

    @Nested
    @DisplayName("成功执行")
    class SuccessCases {

        @Test
        @DisplayName("score=85 命中 r1 → decision=approve")
        void 高分_approve() {
            // Given score=85(>=80)
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("score", 85);
            // When 跑 V1 批测单行
            SubjectResult result = run(fact);
            // Then 成功 + decision=approve
            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) result.output();
            assertThat(output.get("decision")).isEqualTo("approve");
            assertThat(output.get("rejected")).isEqualTo(false);
        }

        @Test
        @DisplayName("score=50 命中 catch-all r2 → decision=reject")
        void 低分_reject() {
            // Given score=50(<80)
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("score", 50);
            // When 跑 V1 批测单行
            SubjectResult result = run(fact);
            // Then 成功 + decision=reject
            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) result.output();
            assertThat(output.get("decision")).isEqualTo("reject");
        }

        @Test
        @DisplayName("output 含 fact 字段(供结果展示 + Simulation 对比)")
        void output含fact() {
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("score", 90);
            SubjectResult result = run(fact);
            assertThat(result.isSuccess()).isTrue();
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) result.output();
            assertThat(output).containsKey("fact");
            @SuppressWarnings("unchecked")
            Map<String, Object> outFact = (Map<String, Object>) output.get("fact");
            assertThat(outFact.get("decision")).isEqualTo("approve");
        }
    }

    @Nested
    @DisplayName("异常处理")
    class ErrorCases {

        @Test
        @DisplayName("缺 bundle 参数 → 失败(INTERNAL 错误码)")
        void 缺bundle_失败() {
            V1BatchTestSubject subject = new V1BatchTestSubject();
            SubjectExecutionContext ctx = new SubjectExecutionContext(
                    1L, 100L, new LinkedHashMap<>(), new LinkedHashMap<>());
            SubjectResult result = subject.execute(ctx);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.errorCode()).isEqualTo("V1_EXECUTION_ERROR");
        }

        @Test
        @DisplayName("getType() 返 V1_FLOW")
        void 类型标识() {
            assertThat(new V1BatchTestSubject().getType()).isEqualTo("V1_FLOW");
        }
    }
}
