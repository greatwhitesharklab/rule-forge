package com.ruleforge.builder;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.library.variable.VariableLibrary;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgePackageImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.3 — {@link KnowledgeBase#getKnowledgePackage} 契约 BDD。
 *
 * <p>锁 V6.3 修法(3-level nested do-while find-first → enhanced for + 2 个 continue)
 * 的行为不变性:
 * <ul>
 *   <li>所有 category (不管 name 是什么) 都被 put 进 variableCategoryMap (name → clazz)</li>
 *   <li>name == "参数" 的 category 才贡献 variables 到 parameters map (var.name → var.type.name())</li>
 *   <li>name == "参数" 但 variables == null 或 empty 的 category 跳过 (不贡献 parameters)</li>
 *   <li>迭代完所有 categories 后返 knowledgePackage (不再走原 do-while 的 hasNext() 返 null 路径)</li>
 *   <li>knowledgePackage 是 cached, 第二次调用返同 instance</li>
 * </ul>
 *
 * <p><b>Why V6.3 选这条</b>: V5.96 doc 立的反编译 var123 + do-while 收尾原则。 V5.96
 * 22 文件主 cleanup 时, KnowledgeBase.getKnowledgePackage 的 3-level nested do-while
 * 被显式 skip ("3-level state machine + 内嵌 for, 中间状态机")。 V6.3 重新审计:
 * 3-level 状态机实际是 find-first pattern (找下一个满足 "name==参数 AND variables!=null
 * AND !variables.isEmpty()" 的 category),可化简为 enhanced for + 2 个 continue +
 * 1 个 inner for — 无 label / 无 skip-pattern / 无 early return,纯 find-first + filter,
 * 1:1 套 V5.96 doc skip 表里 skip-pattern 的反转 (反向收口)。
 *
 * <p>行为关键: 3-level do-while 实际行为是 "process all matching items" (找下一个,
 * 处理, 继续, 找下一个, ...) — 等价 enhanced for + 2 个 continue (filter non-matching
 * + process matching)。 迭代顺序由 `List` iterator 状态决定, 两种写法 iterator 状态
 * 一致 (List 顺序遍历)。
 *
 * <p><b>为什么 V6.3 选 0 perf 信号档</b>: KnowledgeBase.getKnowledgePackage 是
 * build-time 调用 (per-KnowledgePackage, 不是 per-fact), 不在 rete hot path。 JFR
 * 0 sample, wall-time 不受本方法影响。 V6.3 是 pure code elegance closure, 跟 V5.96
 * 22 文件主 cleanup 一档, 不期望 perf 突破。
 *
 * @see com.ruleforge.docs.notes.v633-knowledgebase-dowhile-flatten V6.3 完整 doc
 * @since 6.3
 */
@DisplayName("V6.3 — KnowledgeBase.getKnowledgePackage 契约 (3-level do-while flatten)")
class KnowledgeBaseGetKnowledgePackageTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    /** Build a KnowledgeBase with a Rete backed by the given variableCategories. */
    private static KnowledgeBase kbWithCategories(List<VariableCategory> categories) {
        VariableLibrary vl = new VariableLibrary();
        vl.setVariableCategories(categories);
        ResourceLibrary rl = new ResourceLibrary(List.of(vl), List.of(), List.of());
        Rete rete = new Rete(new ArrayList<>(), rl);
        return new KnowledgeBase(rete);
    }

    private static VariableCategory category(String name, String clazz, List<Variable> variables) {
        VariableCategory c = new VariableCategory();
        c.setName(name);
        c.setClazz(clazz);
        c.setVariables(variables);
        return c;
    }

    private static Variable variable(String name, Datatype type) {
        Variable v = new Variable();
        v.setName(name);
        v.setType(type);
        return v;
    }

    // Cast helpers: variableCategoryMap / parameters are accessed via public getters
    // (note: typo in production code is "Cateogory" not "Category")
    @SuppressWarnings("unchecked")
    private static Map<String, String> varCatMap(KnowledgePackage kp) {
        return (Map<String, String>) ((KnowledgePackageImpl) kp).getVariableCateogoryMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> params(KnowledgePackage kp) {
        return (Map<String, String>) ((KnowledgePackageImpl) kp).getParameters();
    }

    // ─── Empty / boundary cases ───────────────────────────────────────────

    @Nested
    @DisplayName("空 categories: 返 KnowledgePackage, 内部 map 全空")
    class EmptyCategories {

        // Given: variableCategories == empty list
        // When:  kb.getKnowledgePackage()
        // Then:  knowledgePackage 非 null
        //        variableCategoryMap == {} (没 category 可 put)
        //        parameters == {} (没 "参数" category)
        @Test
        @DisplayName("empty variableCategories → 返 KP, variableCategoryMap + parameters 都空")
        void emptyCategoriesProducesEmptyMaps() {
            KnowledgeBase kb = kbWithCategories(Collections.emptyList());
            KnowledgePackage kp = kb.getKnowledgePackage();

            assertThat(kp).isNotNull();
            assertThat(varCatMap(kp)).isEmpty();
            assertThat(params(kp)).isEmpty();
        }
    }

    // ─── non-参数 categories ──────────────────────────────────────────────

    @Nested
    @DisplayName("non-参数 category: variableCategoryMap 有, parameters 跳")
    class NonParamCategory {

        // Given: 1 category named "常量" with 1 Variable
        // When:  kb.getKnowledgePackage()
        // Then:  variableCategoryMap = {"常量" → "java.lang.String"}
        //        parameters = {} (name != "参数", 跳过)
        @Test
        @DisplayName("name != \"参数\" → variableCategoryMap 有, parameters 跳过 (variables 不入 parameters)")
        void nonParamCategorySkipsVariablesForParameters() {
            VariableCategory cat = category("常量", "java.lang.String",
                List.of(variable("foo", Datatype.String)));
            KnowledgeBase kb = kbWithCategories(List.of(cat));
            KnowledgePackage kp = kb.getKnowledgePackage();

            assertThat(varCatMap(kp)).containsExactly(entry("常量", "java.lang.String"));
            assertThat(params(kp)).isEmpty();
        }
    }

    // ─── 参数 with null / empty variables ──────────────────────────────────

    @Nested
    @DisplayName("参数 with null/empty variables: variableCategoryMap 有, parameters 跳")
    class ParamWithNullOrEmptyVariables {

        // Given: 1 category named "参数" with variables == null
        // When:  kb.getKnowledgePackage()
        // Then:  variableCategoryMap = {"参数" → clazz}
        //        parameters = {} (variables null, 跳过)
        @Test
        @DisplayName("name == \"参数\" 但 variables == null → variableCategoryMap 有, parameters 跳")
        void paramWithNullVariablesSkippedForParameters() {
            VariableCategory cat = category("参数", "com.ruleforge.User", null);
            KnowledgeBase kb = kbWithCategories(List.of(cat));
            KnowledgePackage kp = kb.getKnowledgePackage();

            assertThat(varCatMap(kp)).containsExactly(entry("参数", "com.ruleforge.User"));
            assertThat(params(kp)).isEmpty();
        }

        // Given: 1 category named "参数" with variables == [] (empty list)
        // When:  kb.getKnowledgePackage()
        // Then:  variableCategoryMap = {"参数" → clazz}
        //        parameters = {} (variables empty, 跳过)
        @Test
        @DisplayName("name == \"参数\" 但 variables empty → variableCategoryMap 有, parameters 跳")
        void paramWithEmptyVariablesSkippedForParameters() {
            VariableCategory cat = category("参数", "com.ruleforge.User", new ArrayList<>());
            KnowledgeBase kb = kbWithCategories(List.of(cat));
            KnowledgePackage kp = kb.getKnowledgePackage();

            assertThat(varCatMap(kp)).containsExactly(entry("参数", "com.ruleforge.User"));
            assertThat(params(kp)).isEmpty();
        }
    }

    // ─── Happy path: 参数 with non-empty variables ─────────────────────────

    @Nested
    @DisplayName("happy path: 参数 with non-empty variables → parameters populated")
    class ParamWithNonEmptyVariables {

        // Given: 1 category named "参数" with 2 Variables
        // When:  kb.getKnowledgePackage()
        // Then:  variableCategoryMap = {"参数" → clazz}
        //        parameters = {name1 → "String", name2 → "Integer"} (顺序由 HashMap 决定)
        @Test
        @DisplayName("name == \"参数\" + 2 variables → parameters 含 (name → type.name())")
        void paramWithNonEmptyVariablesPopulatesParameters() {
            VariableCategory cat = category("参数", "com.ruleforge.User",
                List.of(
                    variable("age", Datatype.Integer),
                    variable("name", Datatype.String)));
            KnowledgeBase kb = kbWithCategories(List.of(cat));
            KnowledgePackage kp = kb.getKnowledgePackage();

            assertThat(varCatMap(kp)).containsExactly(entry("参数", "com.ruleforge.User"));
            // HashMap 顺序不保证,用 containsOnly
            assertThat(params(kp)).containsOnly(
                entry("age", "Integer"),
                entry("name", "String"));
        }
    }

    // ─── Multi-category mixed ─────────────────────────────────────────────

    @Nested
    @DisplayName("multi-category: 混合 non-参数 / 参数 null / 参数 empty / 参数 non-empty")
    class MultiCategoryMixed {

        // Given: 4 categories:
        //   [0] "常量" non-empty variables (non-参数, 应跳过 parameters)
        //   [1] "参数" null variables (应跳过 parameters)
        //   [2] "参数" empty variables (应跳过 parameters)
        //   [3] "参数" non-empty variables (应贡献 parameters)
        // When:  kb.getKnowledgePackage()
        // Then:  variableCategoryMap 含全部 4 个 (按 List 顺序,后写覆盖先写)
        //        parameters 仅含 [3] 的 variables
        @Test
        @DisplayName("4 categories 混合: variableCategoryMap 全收, parameters 只收匹配的 \"参数\" + non-null + non-empty")
        void mixedCategoriesFilterCorrectly() {
            VariableCategory c0 = category("常量", "java.lang.String",
                List.of(variable("k", Datatype.String))); // non-参数 → parameters 跳
            VariableCategory c1 = category("参数", "com.ruleforge.User", null); // null → parameters 跳
            VariableCategory c2 = category("参数", "com.ruleforge.User", new ArrayList<>()); // empty → parameters 跳
            VariableCategory c3 = category("参数", "com.ruleforge.User",
                List.of(variable("p1", Datatype.Long))); // non-empty → parameters 收

            KnowledgeBase kb = kbWithCategories(List.of(c0, c1, c2, c3));
            KnowledgePackage kp = kb.getKnowledgePackage();

            // 4 categories 全 put 进 variableCategoryMap (name → clazz), 后写覆盖先写
            // c1, c2, c3 都是 "参数" + "com.ruleforge.User", 最终是同一 entry
            assertThat(varCatMap(kp)).hasSize(2);
            assertThat(varCatMap(kp)).containsKeys("常量", "参数");
            assertThat(varCatMap(kp).get("参数")).isEqualTo("com.ruleforge.User");

            // parameters 只收 c3 (唯一一个 "参数" + non-null + non-empty)
            assertThat(params(kp)).containsExactly(entry("p1", "Long"));
        }
    }

    @Nested
    @DisplayName("multi 参数: 多个 \"参数\" category with non-empty variables")
    class MultiParamCategory {

        // Given: 2 categories named "参数", each with 1 Variable (different name + type)
        // When:  kb.getKnowledgePackage()
        // Then:  parameters = {name1 → type1, name2 → type2} (2 entries, 各自贡献)
        @Test
        @DisplayName("2 个 \"参数\" category 都贡献 variables 到 parameters (按 List 顺序)")
        void twoParamCategoriesBothContribute() {
            VariableCategory c0 = category("参数", "com.ruleforge.User",
                List.of(variable("x", Datatype.Integer)));
            VariableCategory c1 = category("参数", "com.ruleforge.User",
                List.of(variable("y", Datatype.Boolean)));

            KnowledgeBase kb = kbWithCategories(List.of(c0, c1));
            KnowledgePackage kp = kb.getKnowledgePackage();

            assertThat(varCatMap(kp)).containsExactly(entry("参数", "com.ruleforge.User"));
            assertThat(params(kp)).containsOnly(
                entry("x", "Integer"),
                entry("y", "Boolean"));
        }
    }

    // ─── Cache ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cache: 第二次 getKnowledgePackage 返同 instance")
    class CacheReturnsSameInstance {

        // Given: 1 valid category
        // When:  kb.getKnowledgePackage() called 2 times
        // Then:  返同一 instance (this.knowledgePackage 缓存)
        @Test
        @DisplayName("cache: 多次调用返同 KP instance (this.knowledgePackage 复用)")
        void secondCallReturnsCachedInstance() {
            VariableCategory cat = category("参数", "com.ruleforge.User",
                List.of(variable("a", Datatype.String)));
            KnowledgeBase kb = kbWithCategories(List.of(cat));

            KnowledgePackage first = kb.getKnowledgePackage();
            KnowledgePackage second = kb.getKnowledgePackage();

            assertThat(first).isSameAs(second);
        }
    }

    // ─── V6.3 specific: name match is exact, not contains/startsWith ──────

    @Nested
    @DisplayName("V6.3 死代码验证: name match 是精确 \"参数\" 字符串,不是 contains/startsWith")
    class NameMatchIsExact {

        // Given: 1 category named "全局参数" (有 "参数" 子串, 但是 not "参数")
        // When:  kb.getKnowledgePackage()
        // Then:  parameters = {} (name != "参数", 跳过)
        //        variableCategoryMap = {"全局参数" → clazz} (所有 category 都进)
        @Test
        @DisplayName("name = \"全局参数\" (含 \"参数\" 子串) → parameters 跳, 验证 name.equals() 不是 contains()")
        void nameWithSubstringIsNotMatched() {
            VariableCategory cat = category("全局参数", "com.ruleforge.Global",
                List.of(variable("g", Datatype.String)));
            KnowledgeBase kb = kbWithCategories(List.of(cat));
            KnowledgePackage kp = kb.getKnowledgePackage();

            assertThat(varCatMap(kp)).containsExactly(entry("全局参数", "com.ruleforge.Global"));
            assertThat(params(kp)).isEmpty();
        }

        // Given: 1 category named "参数表" (starts with "参数")
        // When:  kb.getKnowledgePackage()
        // Then:  parameters = {} (name != "参数", 跳过)
        @Test
        @DisplayName("name = \"参数表\" (startsWith \"参数\") → parameters 跳, 验证 name.equals() 不是 startsWith()")
        void nameWithPrefixIsNotMatched() {
            VariableCategory cat = category("参数表", "com.ruleforge.ParamTable",
                List.of(variable("t", Datatype.String)));
            KnowledgeBase kb = kbWithCategories(List.of(cat));
            KnowledgePackage kp = kb.getKnowledgePackage();

            assertThat(varCatMap(kp)).containsExactly(entry("参数表", "com.ruleforge.ParamTable"));
            assertThat(params(kp)).isEmpty();
        }
    }
}
