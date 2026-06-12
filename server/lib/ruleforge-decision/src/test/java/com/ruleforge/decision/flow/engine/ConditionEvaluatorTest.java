package com.ruleforge.decision.flow.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.36 A7 — ConditionEvaluator UEL 表达式求值行为规范。
 *
 * <p>Mirror Rust V5.32 {@code condition.rs} 契约:1:1 行为对齐。
 *
 * <p>本测试 8 BDD(覆盖跟 Rust condition_test.rs 一样的场景):
 * <ol>
 *   <li>空 expression 真值(gateway edge 无 condition = "always true")</li>
 *   <li>数字字面量比较(&gt; &lt; == != &gt;= &lt;=)</li>
 *   <li>变量路径解析(单层)</li>
 *   <li>嵌套 Map 点号路径(applicant.age)</li>
 *   <li>字符串相等(单引号 + 双引号)</li>
 *   <li>boolean 字面量 / truthy 引用</li>
 *   <li>null 字面量比较(缺变量 vs null)</li>
 *   <li>无 operator truthy 变量</li>
 * </ol>
 */
@DisplayName("ConditionEvaluator UEL 行为")
class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    private Map<String, Object> vars(Object... pairs) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("Given 空 expression,When evaluate,Then true(gateway 无条件 = 永远真)")
    void empty_expression_is_true() {
        assertTrue(evaluator.evaluate("", vars()));
        assertTrue(evaluator.evaluate("   ", vars()));
        assertTrue(evaluator.evaluate(null, vars()));
    }

    @Test
    @DisplayName("Given 数字字面量比较,When evaluate,Then 6 op 全正确")
    void number_literal_compares() {
        assertTrue(evaluator.evaluate("${1 < 2}", vars()));
        assertFalse(evaluator.evaluate("${1 > 2}", vars()));
        assertTrue(evaluator.evaluate("${3 == 3}", vars()));
        assertTrue(evaluator.evaluate("${3 != 4}", vars()));
        assertTrue(evaluator.evaluate("${3 >= 3}", vars()));
        assertTrue(evaluator.evaluate("${3 <= 3}", vars()));
    }

    @Test
    @DisplayName("Given 单层变量路径,When evaluate,Then 解析 vars 里的值")
    void var_path_resolves() {
        Map<String, Object> v = vars("age", 20);
        assertTrue(evaluator.evaluate("${age >= 18}", v));
        assertFalse(evaluator.evaluate("${age < 18}", v));
    }

    @Test
    @DisplayName("Given 嵌套 Map,When evaluate 点号路径 applicant.age,Then 走 map graph")
    void dotted_path_walks_object() {
        Map<String, Object> applicant = new HashMap<>();
        applicant.put("age", 25);
        applicant.put("income", 12000);
        Map<String, Object> v = vars("applicant", applicant);
        assertTrue(evaluator.evaluate("${applicant.age >= 18}", v));
        assertTrue(evaluator.evaluate("${applicant.income > 10000}", v));
    }

    @Test
    @DisplayName("Given 字符串变量,When evaluate == 比较,Then 单/双引号字面量都接受")
    void string_compares() {
        Map<String, Object> v = vars("name", "alice");
        assertTrue(evaluator.evaluate("${name == 'alice'}", v));
        assertFalse(evaluator.evaluate("${name == 'bob'}", v));
        assertTrue(evaluator.evaluate("${name == \"alice\"}", v));
    }

    @Test
    @DisplayName("Given boolean 字面量,When evaluate,Then 解析为 true/false")
    void boolean_literal() {
        assertTrue(evaluator.evaluate("${true}", vars()));
        assertFalse(evaluator.evaluate("${false}", vars()));
    }

    @Test
    @DisplayName("Given 缺变量 vs null 字面量,When evaluate,Then 缺变量解析为 null,仅 ==/!= 走 null path")
    void missing_var_is_null_and_only_eq_ne_match() {
        // 缺变量 → resolveValue 拿不到 → null;只有 == / != 走 null path
        assertTrue(evaluator.evaluate("${absent == null}", vars()));
        assertFalse(evaluator.evaluate("${absent != null}", vars()));
        // 缺变量 + > → false
        assertFalse(evaluator.evaluate("${absent > 0}", vars()));
    }

    @Test
    @DisplayName("Given 无 operator,When evaluate truthy var,Then 走 resolveVariable + Boolean.TRUE.equals")
    void no_operator_truthy_var() {
        assertTrue(evaluator.evaluate("${flag}", vars("flag", true)));
        assertFalse(evaluator.evaluate("${flag}", vars("flag", false)));
    }

    @Nested
    @DisplayName("V5.36 A7 — UEL 补充")
    class A7UELSupplements {

        @Test
        @DisplayName("Given 字符串不等比较,When evaluate !=,Then 正确")
        void string_not_equals() {
            assertTrue(evaluator.evaluate("${name != 'bob'}", vars("name", "alice")));
            assertFalse(evaluator.evaluate("${name != 'alice'}", vars("name", "alice")));
        }

        @Test
        @DisplayName("Given null 字面量 RHS,When evaluate,Then 跟缺变量匹配")
        void null_literal_rhs() {
            // null 字面量(大小写不敏感)
            assertTrue(evaluator.evaluate("${absent == NULL}", vars()));
            assertTrue(evaluator.evaluate("${absent == Null}", vars()));
            assertFalse(evaluator.evaluate("${absent != null}", vars()));
        }

        @Test
        @DisplayName("Given 双变量 null path(都有值,都 null),When evaluate,Then 走 == 恒等")
        void null_both_sides_eq() {
            // absent 解析为 null,null 字面量 = null → == true
            assertTrue(evaluator.evaluate("${absent == absent}", vars()));
        }

        @Test
        @DisplayName("Given vars 显式 null 值,When evaluate == null,Then 真")
        void vars_set_explicit_null() {
            Map<String, Object> v = new HashMap<>();
            v.put("x", null);
            assertTrue(evaluator.evaluate("${x == null}", v));
        }

        @Test
        @DisplayName("Given 浮点字面量,When evaluate,Then 解析为 double")
        void float_literal_parses() {
            assertTrue(evaluator.evaluate("${1.5 < 2.0}", vars()));
            assertFalse(evaluator.evaluate("${2.5 < 2.0}", vars()));
        }

        @Test
        @DisplayName("Given 没 ${} 包裹的 expression,When evaluate,Then 同样工作(允许无包裹)")
        void expression_without_braces() {
            // Rust 端:Strip ${...} wrapper if present
            assertTrue(evaluator.evaluate("age >= 18", vars("age", 20)));
        }

        @Test
        @DisplayName("Given 大写 NULL 字面量(条件求值大小写不敏感),When evaluate,Then 走 null path")
        void null_literal_case_insensitive() {
            assertTrue(evaluator.evaluate("${absent == NULL}", vars()));
            assertTrue(evaluator.evaluate("${absent == Null}", vars()));
            // 但 ${true} 跟 ${True} 一样解析(Mirror Rust 端)
            assertTrue(evaluator.evaluate("${True}", vars()));
        }
    }
}
