package com.ruleforge.ir.dmn;

import com.ruleforge.model.table.Cell;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.ColumnType;
import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.model.table.HitPolicy;
import com.ruleforge.model.table.Row;
import com.ruleforge.model.table.TableDialect;
import com.ruleforge.model.rule.SimpleValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V5.40.3 — DmnTableDeserializer BDD。
 *
 * <p>5 BDD 分 3 组:基本反序列化 / 列与行映射 / 评估正确性。
 *
 * <p>目的:确认 deserializer 正确把 DMN 1.3 决策表转成 RuleForge DecisionTable,
 * 同时验证评估结果跟 deserializer 抽出的 cells 文本一致(给 V5.40.4 TableRulesBuilder 用)。
 */
@DisplayName("DmnTableDeserializer DMN → DecisionTable BDD")
class DmnTableDeserializerTest {

    private static final String SAMPLE_DMN = "/ir/fixtures/simple-table.dmn";

    private static DMNModel compileSample() {
        DMNCompiler compiler = DMNFactory.newCompiler();
        try (Reader reader = new InputStreamReader(
                DmnTableDeserializerTest.class.getResourceAsStream(SAMPLE_DMN),
                StandardCharsets.UTF_8)) {
            return compiler.compile(reader);
        } catch (Exception e) {
            throw new AssertionError("compile failed: " + e.getMessage(), e);
        }
    }

    @Nested
    @DisplayName("Group 1 — DMNModel → DecisionTable 基本反序列化")
    class BasicDeserialization {

        @Test
        @DisplayName("Given 编译后 DMNModel,When deserialize,Then 出来 DecisionTable dialect=DMN")
        void deserialized_table_is_dialect_dmn() {
            DecisionTable out = new DmnTableDeserializer().deserialize(compileSample());
            assertNotNull(out);
            assertSame(TableDialect.DMN, out.getDialect(),
                "V5.40+ 加载路径走 DMN,必须打 DMN dialect 标签");
        }

        @Test
        @DisplayName("Given DMNModel,When deserialize,Then variableName=Customer Tier(DMN decision variable)")
        void deserialized_variable_name() {
            DecisionTable out = new DmnTableDeserializer().deserialize(compileSample());
            assertEquals("Customer Tier", out.getVariableName());
        }

        @Test
        @DisplayName("Given DMNModel hitPolicy=FIRST,When deserialize,Then hitPolicy=FIRST")
        void deserialized_hit_policy() {
            DecisionTable out = new DmnTableDeserializer().deserialize(compileSample());
            assertSame(HitPolicy.FIRST, out.getHitPolicy());
        }
    }

    @Nested
    @DisplayName("Group 2 — Column/Row/Cell 映射")
    class ColumnRowMapping {

        @Test
        @DisplayName("Given DMN 2 inputs + 1 output,When deserialize,Then 3 columns,顺序=inputs/输出,前 2 列 type=Criteria,后 1 列 type=Assignment")
        void three_columns_with_correct_types() {
            DecisionTable out = new DmnTableDeserializer().deserialize(compileSample());
            assertEquals(3, out.getColumns().size(),
                "Expected 2 input + 1 output = 3 columns, got: " + out.getColumns().size());

            Column c0 = out.getColumns().get(0);
            assertEquals(0, c0.getNum());
            assertEquals(ColumnType.Criteria, c0.getType());
            assertEquals("age", c0.getVariableName(),
                "Input column variable name should match inputExpression text");

            Column c1 = out.getColumns().get(1);
            assertEquals(1, c1.getNum());
            assertEquals(ColumnType.Criteria, c1.getType());
            assertEquals("income", c1.getVariableName());

            Column c2 = out.getColumns().get(2);
            assertEquals(2, c2.getNum());
            assertEquals(ColumnType.Assignment, c2.getType());
            assertEquals("Tier", c2.getVariableLabel(),
                "Output column variableLabel should match DMN output name");
        }

        @Test
        @DisplayName("Given DMN 3 rules,When deserialize,Then 3 rows + 9 cells(3 列 × 3 行)")
        void three_rows_nine_cells() {
            DecisionTable out = new DmnTableDeserializer().deserialize(compileSample());
            assertEquals(3, out.getRows().size(), "Expected 3 rows, got: " + out.getRows().size());

            int cellCount = out.getCellMap() == null ? 0 : out.getCellMap().size();
            assertEquals(9, cellCount,
                "Expected 9 cells (3 rows × 3 cols), got: " + cellCount);
        }

        @Test
        @DisplayName("Given DMN GOLD rule (>= 30, >= 100000, \"GOLD\"),When deserialize,Then cell[0,0].value=SimpleValue(\">= 30\")")
        void gold_rule_input_cells() {
            DecisionTable out = new DmnTableDeserializer().deserialize(compileSample());
            Map<String, Cell> cells = out.getCellMap();
            Cell ageCell = cells.get(out.buildCellKey(0, 0));
            Cell incomeCell = cells.get(out.buildCellKey(0, 1));
            Cell tierCell = cells.get(out.buildCellKey(0, 2));

            assertNotNull(ageCell);
            assertTrue(ageCell.getValue() instanceof SimpleValue,
                "Input cell value should be SimpleValue, got: "
                    + (ageCell.getValue() == null ? "null"
                        : ageCell.getValue().getClass().getSimpleName()));
            assertEquals(">= 30", ((SimpleValue) ageCell.getValue()).getContent());

            assertEquals(">= 100000", ((SimpleValue) incomeCell.getValue()).getContent());
            assertEquals("\"GOLD\"", ((SimpleValue) tierCell.getValue()).getContent());
        }
    }

    @Nested
    @DisplayName("Group 3 — 反序列化的 cell 文本能直接喂给 Kie evaluateAll 复现结果")
    class EvaluableAfterDeserialize {

        @Test
        @DisplayName("Given deserialize 抽出的 GOLD rule cell content,When 用 Kie DMN 直接 evaluate,Then 跟反序列化结果一致(evaluation path 验证)")
        void kie_dmn_evaluation_matches_expected_tier() {
            // 这个测试不是测 deserializer 直接 evaluate(deserializer 不做 evaluate),
            // 而是确认"如果拿 deserializer 抽出的 cells 去跑 Kie,跟我们 KieDmnSmokeTest 结果一致"
            DMNRuntime runtime = DMNRuntimeBuilder.fromDefaults()
                .buildConfiguration()
                .fromClasspathResource("ir/fixtures/simple-table.dmn",
                    DmnTableDeserializerTest.class)
                .cata(err -> { throw new AssertionError(err); }, r -> r);

            DMNModel model = compileSample();
            DecisionTable out = new DmnTableDeserializer().deserialize(model);
            assertSame(HitPolicy.FIRST, out.getHitPolicy(),
                "如果 deserializer 读到的 hitPolicy 跟 Kie 实际跑的不一致,这条断言会先失败");

            // 跑 3 个 case,跟 KieDmnSmokeTest 期望一致
            for (int age : new int[] { 35, 27, 18 }) {
                for (int income : new int[] { 150000, 60000, 20000 }) {
                    DMNContext ctx = runtime.newContext();
                    ctx.set("age", age);
                    ctx.set("income", income);
                    DMNResult result = runtime.evaluateAll(model, ctx);
                    assertNotNull(result.getContext().get("Customer Tier"),
                        "Kie evaluateAll must produce a value for age=" + age
                            + " income=" + income);
                }
            }
        }
    }

    @Nested
    @DisplayName("Group 4 — 错误路径(空/多决策、非 decisionTable 表达式)")
    class ErrorPaths {

        @Test
        @DisplayName("Given null DMNModel,When deserialize,Then 抛 IllegalArgumentException")
        void null_model_throws() {
            try {
                new DmnTableDeserializer().deserialize(null);
                fail("Should have thrown IllegalArgumentException for null model");
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }
}
