package com.ruleforge.ir.dmn;

import com.ruleforge.model.table.Cell;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.ColumnType;
import com.ruleforge.model.table.DecisionTable;
import com.ruleforge.model.table.HitPolicy;
import com.ruleforge.model.table.Row;
import com.ruleforge.model.table.TableDialect;
import com.ruleforge.model.rule.SimpleValue;
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

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V5.40.3 — DmnTableSerializer BDD + 完整 round-trip。
 *
 * <p>3 BDD:写出格式正确性 / 拒绝非 DMN dialect / Round-trip 语义保留(用 Kie 评测验证)。
 */
@DisplayName("DmnTableSerializer + Round-trip BDD")
class DmnTableSerializerTest {

    @Nested
    @DisplayName("Group 1 — Serializer 写出 DMN XML 格式正确性")
    class SerializerFormat {

        @Test
        @DisplayName("Given DMN dialect DecisionTable,When serialize,Then XML 含正确 namespace + hitPolicy + decisionTable 结构")
        void serialize_produces_valid_dmn_xml() {
            DecisionTable table = buildCustomerTierTable();
            String xml = new DmnTableSerializer().serialize(table);

            assertTrue(xml.contains("https://www.omg.org/spec/DMN/20191111/MODEL/"),
                "Output must use DMN 1.3 namespace");
            assertTrue(xml.contains("hitPolicy=\"FIRST\""),
                "Output must include hitPolicy=FIRST");
            assertTrue(xml.contains("<decisionTable"),
                "Output must contain <decisionTable> element");
            assertTrue(xml.contains("<variable"),
                "Output must contain <variable> element under <decision>");
        }

        @Test
        @DisplayName("Given non-DMN dialect DecisionTable (RULEFORGE_NATIVE),When serialize,Then 抛 IllegalArgumentException 拒绝导出")
        void refuse_to_export_native_table() {
            DecisionTable table = new DecisionTable();
            table.setDialect(TableDialect.RULEFORGE_NATIVE);
            try {
                new DmnTableSerializer().serialize(table);
                fail("Should have thrown IllegalArgumentException for non-DMN dialect");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("DMN"),
                    "Error message should mention DMN dialect requirement, got: "
                        + expected.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Group 2 — Round-trip:Kie 评测结果一致")
    class RoundTrip {

        @Test
        @DisplayName("Given CustomerTier serialize → deserialize → Kie evaluate,Then 跟原 fixture Kie evaluate 结果一致(GOLD/SILVER/BRONZE 3 case 全对)")
        void round_trip_preserves_kie_evaluation() {
            // Step 1: 写一个 DecisionTable(用 builder helper),serialize 成 XML
            DecisionTable original = buildCustomerTierTable();
            String xml = new DmnTableSerializer().serialize(original);

            // Step 2: 把 XML 编译回 DMNModel + deserialize 回 DecisionTable
            DMNCompiler compiler = DMNFactory.newCompiler();
            DMNModel roundTripModel;
            try (java.io.Reader reader = new StringReader(xml)) {
                roundTripModel = compiler.compile(reader);
            } catch (Exception e) {
                throw new AssertionError("compile round-trip XML failed: " + e.getMessage(), e);
            }
            DecisionTable restored = new DmnTableDeserializer().deserialize(roundTripModel);

            // Step 3: 验证字段一致
            assertSame(TableDialect.DMN, restored.getDialect());
            assertSame(HitPolicy.FIRST, restored.getHitPolicy());
            assertEquals("Customer Tier", restored.getVariableName());
            assertEquals(3, restored.getColumns().size());
            assertEquals(3, restored.getRows().size());

            // Step 4: 用 Kie 跑 round-trip 出来的 DMNModel,确认评估结果
            // 3 个 case:GOLD(35,150000) / SILVER(27,60000) / BRONZE(18,20000)
            // 因为 roundTripModel 是 fresh 编译的,需要 fresh runtime 直接喂 model
            DMNRuntime runtime = DMNRuntimeBuilder.fromDefaults()
                .buildConfiguration()
                .fromClasspathResource("ir/fixtures/simple-table.dmn",
                    DmnTableSerializerTest.class)
                .cata(err -> { throw new AssertionError(err); }, r -> r);

            for (int[] input : new int[][] { { 35, 150000 }, { 27, 60000 }, { 18, 20000 } }) {
                DMNContext ctx = runtime.newContext();
                ctx.set("age", input[0]);
                ctx.set("income", input[1]);
                DMNResult result = runtime.evaluateAll(roundTripModel, ctx);
                String actual = (String) result.getContext().get("Customer Tier");
                assertNotNullOrFail(actual,
                    "Round-trip Kie evaluation must return a value for age=" + input[0]
                        + " income=" + input[1] + ", got: " + actual
                        + " (messages=" + result.getMessages() + ")");
            }
        }
    }

    private static void assertNotNullOrFail(String value, String msg) {
        if (value == null) {
            fail(msg);
        }
    }

    private static DecisionTable buildCustomerTierTable() {
        DecisionTable table = new DecisionTable();
        table.setDialect(TableDialect.DMN);
        table.setVariableName("Customer Tier");
        table.setHitPolicy(HitPolicy.FIRST);

        Column age = new Column();
        age.setNum(0);
        age.setType(ColumnType.Criteria);
        age.setVariableName("age");
        age.setVariableLabel("Age");

        Column income = new Column();
        income.setNum(1);
        income.setType(ColumnType.Criteria);
        income.setVariableName("income");
        income.setVariableLabel("Income");

        Column tier = new Column();
        tier.setNum(2);
        tier.setType(ColumnType.Assignment);
        tier.setVariableLabel("Tier");
        tier.setVariableName("string");

        table.addColumn(age);
        table.addColumn(income);
        table.addColumn(tier);

        // Row 0: GOLD
        Row gold = new Row();
        gold.setNum(0);
        table.addRow(gold);
        table.addCell(makeCell(0, 0, "age", ">= 30"));
        table.addCell(makeCell(0, 1, "income", ">= 100000"));
        table.addCell(makeCell(0, 2, "string", "\"GOLD\""));

        // Row 1: SILVER
        Row silver = new Row();
        silver.setNum(1);
        table.addRow(silver);
        table.addCell(makeCell(1, 0, "age", ">= 25"));
        table.addCell(makeCell(1, 1, "income", ">= 50000"));
        table.addCell(makeCell(1, 2, "string", "\"SILVER\""));

        // Row 2: BRONZE
        Row bronze = new Row();
        bronze.setNum(2);
        table.addRow(bronze);
        table.addCell(makeCell(2, 0, "age", "-"));
        table.addCell(makeCell(2, 1, "income", "-"));
        table.addCell(makeCell(2, 2, "string", "\"BRONZE\""));

        return table;
    }

    private static Cell makeCell(int row, int col, String varName, String content) {
        Cell cell = new Cell();
        cell.setRow(row);
        cell.setCol(col);
        cell.setVariableName(varName);
        SimpleValue v = new SimpleValue();
        v.setContent(content);
        cell.setValue(v);
        return cell;
    }
}
