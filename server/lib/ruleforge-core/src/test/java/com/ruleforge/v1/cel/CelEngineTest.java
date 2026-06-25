package com.ruleforge.v1.cel;

import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.SchemaField;
import com.ruleforge.v1.ast.V1DataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V7.0.0 W1-2 — CEL 引擎(cel-java)接入 + 校验 BDD。
 *
 * <p>锁 CEL 边界(design doc Block 3):
 * <ul>
 *   <li>合法 CEL condition 编译通过 + 类型 = boolean(按 schema 声明变量类型检查)</li>
 *   <li>语法错 / 未声明变量 / 非 boolean 返回 → CelConditionException</li>
 *   <li>evalBoolean 对 fact bindings 正确求值(算术/比较/逻辑/in 列表成员)</li>
 *   <li>CEL 天然无赋值(pure expression)</li>
 * </ul>
 */
@DisplayName("V7.0.0 W1-2 — CEL 引擎校验 + 求值")
class CelEngineTest {

    /** 现金贷 fact schema:age/score/income=NUMBER,blacklisted=BOOLEAN,tags=LIST,decision=STRING。 */
    private Schema loanSchema() {
        Schema s = new Schema();
        s.setName("LoanApplication");
        s.setFields(List.of(
                new SchemaField("age", V1DataType.NUMBER),
                new SchemaField("score", V1DataType.NUMBER),
                new SchemaField("income", V1DataType.NUMBER),
                new SchemaField("blacklisted", V1DataType.BOOLEAN),
                new SchemaField("tags", V1DataType.LIST),
                new SchemaField("decision", V1DataType.STRING)));
        return s;
    }

    @Nested
    @DisplayName("Given 合法 CEL condition When compileBoolean Then 通过")
    class CompileValid {

        @Test
        void age_ge_18_编译通过() {
            // Given "age >= 18" + schema When compileBoolean Then 不抛
            assertThat(CelEngine.compileBoolean("age >= 18", loanSchema())).isNotNull();
        }

        @Test
        void 复合条件_score_and_blacklisted_编译通过() {
            assertThat(CelEngine.compileBoolean("score > 600 && !blacklisted", loanSchema())).isNotNull();
        }

        @Test
        void 列表成员_in_编译通过() {
            // Given "vip in tags" When compileBoolean Then 通过(列表成员用 in,非 contains)
            assertThat(CelEngine.compileBoolean("'vip' in tags", loanSchema())).isNotNull();
        }
    }

    @Nested
    @DisplayName("Given 非法 CEL When compileBoolean Then CelConditionException")
    class CompileInvalid {

        @Test
        void 非_boolean_返回值_被拒() {
            // Given "age" (返回 double 非 bool) When compileBoolean Then 抛 "必须返回 boolean"
            assertThatThrownBy(() -> CelEngine.compileBoolean("age", loanSchema()))
                    .isInstanceOf(CelConditionException.class)
                    .hasMessageContaining("必须返回 boolean");
        }

        @Test
        void 语法错_被拒() {
            // Given "age >= " When compileBoolean Then 抛
            assertThatThrownBy(() -> CelEngine.compileBoolean("age >= ", loanSchema()))
                    .isInstanceOf(CelConditionException.class);
        }

        @Test
        void 未声明变量_编译期_被拒() {
            // Given "undefined_var > 5" When compileBoolean Then 抛(CEL checker 要求声明)
            assertThatThrownBy(() -> CelEngine.compileBoolean("undefined_var > 5", loanSchema()))
                    .isInstanceOf(CelConditionException.class)
                    .hasMessageContaining("undeclared");
        }
    }

    @Nested
    @DisplayName("Given fact bindings When evalBoolean Then 正确求值")
    class Evaluate {

        @Test
        void age_ge_18_对_30_岁_true() {
            assertThat(CelEngine.evalBoolean("age >= 18", Map.of("age", 30), loanSchema())).isTrue();
        }

        @Test
        void age_ge_18_对_15_岁_false() {
            assertThat(CelEngine.evalBoolean("age >= 18", Map.of("age", 15), loanSchema())).isFalse();
        }

        @Test
        void 复合条件_求值() {
            Map<String, Object> fact = Map.of("score", 700, "blacklisted", false);
            assertThat(CelEngine.evalBoolean("score > 600 && !blacklisted", fact, loanSchema())).isTrue();
        }

        @Test
        void 复合条件_blacklisted_true_短路_false() {
            Map<String, Object> fact = Map.of("score", 700, "blacklisted", true);
            assertThat(CelEngine.evalBoolean("score > 600 && !blacklisted", fact, loanSchema())).isFalse();
        }

        @Test
        void 列表成员_in_求值() {
            // Given tags=["vip","new"] When eval "'vip' in tags" Then true
            Map<String, Object> fact = Map.of("tags", List.of("vip", "new"));
            assertThat(CelEngine.evalBoolean("'vip' in tags", fact, loanSchema())).isTrue();
        }

        @Test
        void number_int_double_自动统一() {
            // Given Integer 700 When eval "score > 600" (score 声明 DOUBLE) Then true
            // (coerceNumbersToDouble 把 Integer 700 → Double 700.0)
            assertThat(CelEngine.evalBoolean("score > 600", Map.of("score", 700), loanSchema())).isTrue();
        }
    }

    @Nested
    @DisplayName("Given POJO fact When toBindings Then CEL 可求值")
    class PojoBindings {

        static class LoanFact {
            public int getAge() { return 30; }
            public int getScore() { return 700; }
            public boolean isBlacklisted() { return false; }
        }

        @Test
        void pojo_fact_转_map_后_cel_可求值() {
            Map<String, Object> bindings = CelEngine.toBindings(new LoanFact());
            assertThat(bindings).containsKeys("age", "score", "blacklisted");
            assertThat(CelEngine.evalBoolean("age >= 18 && score > 600", bindings, loanSchema())).isTrue();
        }
    }
}
