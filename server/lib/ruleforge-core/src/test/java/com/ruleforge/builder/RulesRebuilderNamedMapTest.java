package com.ruleforge.builder;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.variable.Act;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V6.9 — {@link RulesRebuilder#getVariableByName}/{@link RulesRebuilder#getVariableByLabel}
 * 的 namedMap 重命名 category 行为不变性 characterization test BDD。
 *
 * <p>锁 V6.9 优化 (2 处 {@code containsKey + get} 双 lookup → {@code get + null check} 单 lookup) 的行为不变性:
 * <ul>
 *   <li><b>namedMap null</b>:跳过重命名步骤,直接用原 category 名找</li>
 *   <li><b>namedMap 空 map</b>:等价于 null(无重命名)</li>
 *   <li><b>namedMap 含 category</b>:用 value 重命名后再找</li>
 *   <li><b>namedMap 不含 category</b>:用原 category 名找(行为同旧 containsKey==false)</li>
 *   <li><b>rename 后找不到</b>:抛 RuleException("Variable [...] was not found.")</li>
 * </ul>
 *
 * <p><b>Why V6.9 选这条</b>: V5.93 原则系列 (V5.100.0-3 + V6.1) 的最后一处 build-time 路径。
 * value 永为 {@code String} (旧 namedMap.put(key, null) 假设不存 — 用法上 namedMap 是 category
 * 重命名表,正常用法 put non-null value)。{@code Map.get(key)} 在 absent 时返 null,等价于
 * {@code !containsKey(key)}。节省 1 个 containsKey hash lookup per call (build-time
 * per-rule-build 路径, JFR 0 sample 预期, perf 收益小但延续 V5.93 套用风格)。
 *
 * <p><b>不动内层循环</b>: 两方法的 for-loop 遍历 {@code variableCategories} 已经是 enhanced for,
 * 内层 var 比较逻辑 (V5.96 已收口) 不动。V6.9 只砍 namedMap.containsKey guard。
 */
@DisplayName("V6.9 — RulesRebuilder.getVariableByName/Label namedMap 重命名契约")
class RulesRebuilderNamedMapTest {

    private final RulesRebuilder rebuilder = new RulesRebuilder();

    private Variable makeVariable(String name, String label) {
        Variable v = new Variable();
        v.setName(name);
        v.setLabel(label);
        v.setType(Datatype.String);
        v.setAct(Act.In);
        return v;
    }

    private VariableCategory makeCategory(String name, Variable... variables) {
        VariableCategory c = new VariableCategory();
        c.setName(name);
        c.setType(com.ruleforge.model.library.variable.CategoryType.Clazz);
        c.setClazz("com.ruleforge.model.GeneralEntity");
        List<Variable> vars = new ArrayList<>();
        for (Variable v : variables) vars.add(v);
        c.setVariables(vars);
        return c;
    }

    @Nested
    @DisplayName("namedMap null — 跳过重命名")
    class NamedMapNull {

        // Given: 一个 category "User" 含 variable "name"
        // When:  getVariableByName 用 namedMap=null
        // Then:  找到 "name" (用原 category "User" 查, 不抛)
        @Test
        @DisplayName("namedMap=null 应用原 category 查找")
        void nullMapUsesOriginalCategory() {
            Variable var = makeVariable("name", "name");
            VariableCategory cat = makeCategory("User", var);

            Variable result = rebuilder.getVariableByName(
                Collections.singletonList(cat), "User", "name", null);

            assertThat(result).isSameAs(var);
        }

        // Given: 同上
        // When:  getVariableByLabel 用 namedMap=null
        // Then:  找到 label
        @Test
        @DisplayName("getVariableByLabel namedMap=null 应用原 category 查找")
        void nullMapByLabelUsesOriginalCategory() {
            Variable var = makeVariable("name", "username");
            VariableCategory cat = makeCategory("User", var);

            Variable result = rebuilder.getVariableByLabel(
                Collections.singletonList(cat), "User", "username", null);

            assertThat(result).isSameAs(var);
        }
    }

    @Nested
    @DisplayName("namedMap 空 — 等价于 null")
    class NamedMapEmpty {

        // Given: category "User" + 空 namedMap
        // When:  getVariableByName 用 namedMap=empty
        // Then:  找到 (空 map 无 rename, 行为同 null)
        @Test
        @DisplayName("namedMap=empty 无 rename, 行为同 null")
        void emptyMapNoRename() {
            Variable var = makeVariable("name", "name");
            VariableCategory cat = makeCategory("User", var);

            Variable result = rebuilder.getVariableByName(
                Collections.singletonList(cat), "User", "name", new HashMap<>());

            assertThat(result).isSameAs(var);
        }
    }

    @Nested
    @DisplayName("namedMap 含 category — rename 后查找")
    class NamedMapRename {

        // Given: category "User" 含 "name" + namedMap "User" → "Person"
        // When:  getVariableByName 用 category="User" + namedMap
        // Then:  应 rename 到 "Person" 后找 "Person.name", 但找不到 → 抛
        @Test
        @DisplayName("rename 到不存在的 category 应抛 RuleException")
        void renameToMissingCategoryThrows() {
            Variable var = makeVariable("name", "name");
            VariableCategory cat = makeCategory("User", var);

            Map<String, String> namedMap = new HashMap<>();
            namedMap.put("User", "Person");

            assertThatThrownBy(() -> rebuilder.getVariableByName(
                Collections.singletonList(cat), "User", "name", namedMap))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Variable [Person.name]");
        }

        // Given: 两个 category "User" + "Person" + namedMap "User" → "Person"
        // When:  getVariableByName 用 category="User" + namedMap
        // Then:  应 rename 到 "Person" 后找到 "Person.name"
        @Test
        @DisplayName("rename 到存在的 category 应找到 variable (核心契约)")
        void renameToExistingCategoryFindsVariable() {
            Variable userVar = makeVariable("name", "name");
            VariableCategory userCat = makeCategory("User", userVar);

            Variable personVar = makeVariable("name", "name");
            VariableCategory personCat = makeCategory("Person", personVar);

            List<VariableCategory> categories = new ArrayList<>();
            categories.add(userCat);
            categories.add(personCat);

            Map<String, String> namedMap = new HashMap<>();
            namedMap.put("User", "Person");

            Variable result = rebuilder.getVariableByName(categories, "User", "name", namedMap);
            assertThat(result).isSameAs(personVar);
        }

        // Given: category "User" + namedMap 不含 "User"
        // When:  getVariableByName 用 category="User"
        // Then:  应不动 category, 找到 (跟旧 containsKey==false 行为一致)
        @Test
        @DisplayName("namedMap 不含 category → 用原 category 查找 (旧 containsKey==false 语义)")
        void namedMapMissingKeyUsesOriginalCategory() {
            Variable var = makeVariable("name", "name");
            VariableCategory cat = makeCategory("User", var);

            Map<String, String> namedMap = new HashMap<>();
            namedMap.put("Other", "SomeOther");  // 不含 "User"

            Variable result = rebuilder.getVariableByName(
                Collections.singletonList(cat), "User", "name", namedMap);
            assertThat(result).isSameAs(var);
        }
    }

    @Nested
    @DisplayName("getVariableByLabel — namedMap rename (mirror test)")
    class ByLabelRename {

        // Given: 两个 category "User" + "Person" + namedMap "User" → "Person"
        // When:  getVariableByLabel 用 category="User" + label "person_name"
        // Then:  应 rename 到 "Person" 后找到 "Person.person_name"
        @Test
        @DisplayName("getVariableByLabel rename 应找到 renamed category 的 label")
        void byLabelRenameToExistingCategory() {
            Variable userVar = makeVariable("name", "user_name");
            VariableCategory userCat = makeCategory("User", userVar);

            Variable personVar = makeVariable("name", "person_name");
            VariableCategory personCat = makeCategory("Person", personVar);

            List<VariableCategory> categories = new ArrayList<>();
            categories.add(userCat);
            categories.add(personCat);

            Map<String, String> namedMap = new HashMap<>();
            namedMap.put("User", "Person");

            Variable result = rebuilder.getVariableByLabel(categories, "User", "person_name", namedMap);
            assertThat(result).isSameAs(personVar);
        }
    }
}