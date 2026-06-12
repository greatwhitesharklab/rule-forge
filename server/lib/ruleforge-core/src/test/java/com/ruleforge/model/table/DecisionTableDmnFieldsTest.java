package com.ruleforge.model.table;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * V5.40.2 — DecisionTable 4 个新字段(hitPolicy / aggregation / dialect / variableName)
 * BDD 验证。
 *
 * <p>目的:V5.40 切 IR 决策表 → DMN 1.3 时,DecisionTable model 需要承载:
 * <ol>
 *   <li>{@link HitPolicy} — 从 .dmn 的 {@code <decisionTable hitPolicy="...">} 读,老 .xml 默认 null</li>
 *   <li>{@link Aggregation} — 从 .dmn 的 {@code <decisionTable aggregation="...">} 读,老 .xml 默认 null</li>
 *   <li>{@link TableDialect} — 标志"这个表来自哪条加载路径",V5.40 之前一律 RULEFORGE_NATIVE,
 *       V5.40+ 写的新表是 DMN。null = 兼容老用法,行为等同 RULEFORGE_NATIVE</li>
 *   <li>{@code variableName} — DMN decision 节点输出变量名,evaluateAll 输出 key</li>
 * </ol>
 *
 * <p>5 个 BDD 分 2 组:基本字段读写 / 默认值兼容性。
 */
@DisplayName("DecisionTable DMN 字段 BDD")
class DecisionTableDmnFieldsTest {

    @Nested
    @DisplayName("Group 1 — 4 个新字段基本读写")
    class FieldReadWrite {

        @Test
        @DisplayName("Given 新 DecisionTable,When setHitPolicy(FIRST) + getHitPolicy(),Then 返回 FIRST")
        void hit_policy_set_get() {
            DecisionTable table = new DecisionTable();
            assertNull(table.getHitPolicy(), "新 DecisionTable hitPolicy 默认 null");

            table.setHitPolicy(HitPolicy.FIRST);
            assertSame(HitPolicy.FIRST, table.getHitPolicy());
        }

        @Test
        @DisplayName("Given DecisionTable,When setAggregation(SUM),Then 读出 SUM")
        void aggregation_set_get() {
            DecisionTable table = new DecisionTable();
            assertNull(table.getAggregation(), "新 DecisionTable aggregation 默认 null");

            table.setAggregation(Aggregation.SUM);
            assertSame(Aggregation.SUM, table.getAggregation());
        }

        @Test
        @DisplayName("Given DecisionTable,When setDialect(DMN),Then 读出 DMN")
        void dialect_set_get() {
            DecisionTable table = new DecisionTable();
            assertNull(table.getDialect(), "新 DecisionTable dialect 默认 null(兼容老用法)");

            table.setDialect(TableDialect.DMN);
            assertSame(TableDialect.DMN, table.getDialect());
        }

        @Test
        @DisplayName("Given DecisionTable,When setVariableName(Customer Tier),Then 读出 Customer Tier")
        void variable_name_set_get() {
            DecisionTable table = new DecisionTable();
            assertNull(table.getVariableName(), "新 DecisionTable variableName 默认 null");

            table.setVariableName("Customer Tier");
            assertEquals("Customer Tier", table.getVariableName());
        }
    }

    @Nested
    @DisplayName("Group 2 — 默认值 + 老用法兼容性")
    class DefaultValuesAndCompat {

        @Test
        @DisplayName("Given 新 DecisionTable,When 不 set 任何 V5.40 字段,Then 4 个新字段都是 null,等价 V5.39 老用法")
        void default_values_match_v539_compat() {
            DecisionTable table = new DecisionTable();

            assertNull(table.getHitPolicy(), "V5.39 老用法 hitPolicy 不该被默认填值");
            assertNull(table.getAggregation(), "V5.39 老用法 aggregation 不该被默认填值");
            assertNull(table.getDialect(), "V5.39 老用法 dialect 不该被默认填值");
            assertNull(table.getVariableName(), "V5.39 老用法 variableName 不该被默认填值");
        }

        @Test
        @DisplayName("Given DMN 完整 4 字段填齐的 DecisionTable,When 4 个 getter 一起读,Then 全部正确")
        void all_four_fields_filled_for_dmn_table() {
            DecisionTable table = new DecisionTable();
            table.setHitPolicy(HitPolicy.UNIQUE);
            table.setAggregation(Aggregation.COUNT);
            table.setDialect(TableDialect.DMN);
            table.setVariableName("RiskScore");

            assertSame(HitPolicy.UNIQUE, table.getHitPolicy());
            assertSame(Aggregation.COUNT, table.getAggregation());
            assertSame(TableDialect.DMN, table.getDialect());
            assertEquals("RiskScore", table.getVariableName());
        }

        @Test
        @DisplayName("Given 7 种 HitPolicy enum,When values(),Then 全部存在")
        void hit_policy_enum_complete() {
            HitPolicy[] values = HitPolicy.values();
            assertEquals(7, values.length, "DMN 1.3 决策表有 7 种 hit policy");
            assertNotNull(HitPolicy.valueOf("UNIQUE"));
            assertNotNull(HitPolicy.valueOf("FIRST"));
            assertNotNull(HitPolicy.valueOf("PRIORITY"));
            assertNotNull(HitPolicy.valueOf("ANY"));
            assertNotNull(HitPolicy.valueOf("COLLECT"));
            assertNotNull(HitPolicy.valueOf("RULE_ORDER"));
            assertNotNull(HitPolicy.valueOf("OUTPUT_ORDER"));
        }

        @Test
        @DisplayName("Given 5 种 Aggregation enum,When values(),Then 全部存在")
        void aggregation_enum_complete() {
            Aggregation[] values = Aggregation.values();
            assertEquals(5, values.length, "DMN 1.3 决策表 aggregation 有 5 种");
            assertNotNull(Aggregation.valueOf("SUM"));
            assertNotNull(Aggregation.valueOf("COUNT"));
            assertNotNull(Aggregation.valueOf("MIN"));
            assertNotNull(Aggregation.valueOf("MAX"));
            assertNotNull(Aggregation.valueOf("NONE"));
        }
    }
}
