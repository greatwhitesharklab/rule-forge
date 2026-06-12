package com.ruleforge.core.ir.dmn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNCompiler;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.40 — Kie DMN 10.1.0 加载验证 (Sample BDD)。
 *
 * <p>5 BDD 分 3 组:基本加载 / 决策表提取 / 评估可执行。
 *
 * <p>目的:V5.40 PR 切决策表→DMN 的"前置条件"——Kie DMN 10.1.0 能
 * (1) 编译 .dmn 文件 (2) 提取 DMNModel / Decision (3) 实际 evaluateAll 出结果。
 * 此测试通过 → 后续 DmnTableDeserializer 可基于 Kie DMN 公开 API
 * (DMNCompiler / DMNRuntime / DMNModel) 实现。
 *
 * <p>版本:kie-dmn-core 10.1.0 (Kogito 10 稳定线)。
 * 公开 API 字节码验证 8.44.2 / 10.1.0 / 10.2.0 三版本完全一致。
 */
@DisplayName("Kie DMN 10.1.0 加载 sample BDD")
class KieDmnSmokeTest {

    private static final String SAMPLE_DMN = "/ir/fixtures/simple-table.dmn";

    @Nested
    @DisplayName("Group 1 — DMNCompiler 加载 .dmn")
    class CompilerLoadsDmn {

        @Test
        @DisplayName("Given 简单 .dmn 决策表 (3 rules, FIRST hit policy),When DMNFactory.newCompiler().compile(reader),Then DMNModel 不为 null 且 name=CustomerTier")
        void compile_simple_dmn_yields_dmnmodel() {
            DMNCompiler compiler = DMNFactory.newCompiler();
            assertNotNull(compiler, "DMNFactory.newCompiler() should return a non-null DMNCompiler");

            try (Reader reader = openSample()) {
                DMNModel model = compiler.compile(reader);
                assertNotNull(model, "compile(Reader) should return a non-null DMNModel");
                assertEquals("CustomerTier", model.getName());
            } catch (Exception e) {
                throw new AssertionError("compile failed: " + e.getMessage(), e);
            }
        }
    }

    @Nested
    @DisplayName("Group 2 — DMNModel 决策表提取")
    class DmnModelDecisionExtraction {

        @Test
        @DisplayName("Given 编译后 DMNModel,When getDecisions(),Then 返回 1 个 Decision 节点(name=Customer Tier)")
        void dmnmodel_has_one_decision_node() {
            DMNModel model = compileSample();
            assertEquals(1, model.getDecisions().size(),
                "Expected 1 decision node, got: " + model.getDecisions().size());
            assertEquals("Customer Tier", model.getDecisions().iterator().next().getName());
        }

        @Test
        @DisplayName("Given DMNModel,When getInputs(),Then 返回 2 个 InputData 节点(age / income)")
        void dmnmodel_has_two_inputs() {
            DMNModel model = compileSample();
            assertEquals(2, model.getInputs().size(),
                "Expected 2 input data nodes, got: " + model.getInputs().size());
        }
    }

    @Nested
    @DisplayName("Group 3 — DMNRuntime 评估")
    class DmnRuntimeEvaluation {

        @Test
        @DisplayName("Given DMNRuntime + DMNModel,When evaluateAll(age=35, income=150000),Then 决策结果=GOLD")
        void evaluate_gold_tier() {
            DMNRuntime runtime = newRuntime();
            DMNModel model = compileSample();

            DMNContext ctx = runtime.newContext();
            ctx.set("age", 35);
            ctx.set("income", 150000);

            DMNResult result = runtime.evaluateAll(model, ctx);
            assertTrue(result.getMessages().isEmpty(),
                "Evaluation should have no errors, got: " + result.getMessages());
            assertEquals("GOLD", result.getContext().get("Customer Tier"));
        }

        @Test
        @DisplayName("Given DMNRuntime + DMNModel,When evaluateAll(age=27, income=60000),Then 决策结果=SILVER")
        void evaluate_silver_tier() {
            DMNRuntime runtime = newRuntime();
            DMNModel model = compileSample();

            DMNContext ctx = runtime.newContext();
            ctx.set("age", 27);
            ctx.set("income", 60000);

            DMNResult result = runtime.evaluateAll(model, ctx);
            assertEquals("SILVER", result.getContext().get("Customer Tier"));
        }

        @Test
        @DisplayName("Given DMNRuntime + DMNModel,When evaluateAll(age=18, income=20000),Then 决策结果=BRONZE(兜底规则)")
        void evaluate_bronze_tier_fallback() {
            DMNRuntime runtime = newRuntime();
            DMNModel model = compileSample();

            DMNContext ctx = runtime.newContext();
            ctx.set("age", 18);
            ctx.set("income", 20000);

            DMNResult result = runtime.evaluateAll(model, ctx);
            assertEquals("BRONZE", result.getContext().get("Customer Tier"));
        }
    }

    // === 辅助 ===

    private static DMNModel compileSample() {
        try (Reader reader = openSample()) {
            return DMNFactory.newCompiler().compile(reader);
        } catch (Exception e) {
            throw new AssertionError("compile failed: " + e.getMessage(), e);
        }
    }

    private static DMNRuntime newRuntime() {
        try {
            return DMNRuntimeBuilder.fromDefaults()
                .buildConfiguration()
                .fromClasspathResource("ir/fixtures/simple-table.dmn",
                    KieDmnSmokeTest.class)
                .cata(
                    err -> { throw new AssertionError("fromClasspathResource failed: " + err); },
                    runtime -> runtime);
        } catch (Exception e) {
            throw new AssertionError("newRuntime failed: " + e.getMessage(), e);
        }
    }

    private static Reader openSample() throws Exception {
        return new InputStreamReader(
            KieDmnSmokeTest.class.getResourceAsStream(SAMPLE_DMN),
            StandardCharsets.UTF_8);
    }
}
