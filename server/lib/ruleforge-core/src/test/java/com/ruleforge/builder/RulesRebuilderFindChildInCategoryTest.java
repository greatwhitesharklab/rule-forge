package com.ruleforge.builder;

import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.constant.Constant;
import com.ruleforge.model.library.constant.ConstantCategory;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V6.9.19 — {@code RulesRebuilder.findChildInCategory} helper extract 行为契约 BDD。
 *
 * <p>锁 V6.9.19 收口 (getConstantByName L584-596 + getConstantByLabel L598-610 +
 * getVariableByName L612-634 + getVariableByLabel L636-655 4 method 14 行 100% 同构
 * pattern 抽 helper `findChildInCategory(categories, category, identifier,
 * categoryNameExtractor, childrenGetter, childMatch, kind)`) 的行为不变性:
 * <ul>
 *   <li><b>hit</b>: 返回第一个匹配 category + child 的元素</li>
 *   <li><b>category 不匹配</b>: 跳过该 category, 继续下一个</li>
 *   <li><b>未找到</b>: 抛 RuleException, message 含 "{kind} [{category}.{identifier}] was not found."</li>
 *   <li><b>多 category 多个 child</b>: 找到正确的那一个 (按 category→child 顺序)</li>
 * </ul>
 *
 * <p><b>Why V6.9.19</b>: v69_pipeline P0-1, RulesRebuilder 4 method 14 行 100%
 * 同构 pattern (linear scan categories → match category → linear scan children →
 * match child → throw on miss)。 V6.9.14 helper extract 模式直接套。
 *
 * <p>改动估算: 4 method × 14 行 → 4 × 5-7 行 + 1 helper ~13 行 = -28 行净减少。
 */
@DisplayName("V6.9.19 — RulesRebuilder.findChildInCategory helper extract 契约")
class RulesRebuilderFindChildInCategoryTest {

    private RulesRebuilder rebuilder;

    @BeforeEach
    void setUp() {
        rebuilder = new RulesRebuilder();
    }

    // ====== ConstantCategory fixtures ======

    private ConstantCategory newConstCategory(String label, Constant... constants) {
        ConstantCategory cat = new ConstantCategory();
        cat.setLabel(label);
        List<Constant> list = new ArrayList<>();
        Collections.addAll(list, constants);
        cat.setConstants(list);
        return cat;
    }

    private Constant newConstant(String name, String label) {
        Constant c = new Constant();
        c.setName(name);
        c.setLabel(label);
        return c;
    }

    // ====== VariableCategory fixtures ======

    private VariableCategory newVarCategory(String name, Variable... vars) {
        VariableCategory cat = new VariableCategory();
        cat.setName(name);
        List<Variable> list = new ArrayList<>();
        Collections.addAll(list, vars);
        cat.setVariables(list);
        return cat;
    }

    private Variable newVariable(String name, String label) {
        Variable v = new Variable();
        v.setName(name);
        v.setLabel(label);
        return v;
    }

    // ====== getConstantByName ======

    @Nested
    @DisplayName("getConstantByName 路径")
    class ConstantByName {

        @Test
        @DisplayName("hit — 单 category + 匹配 name 应返回该 Constant")
        void hit() {
            Constant target = newConstant("FOO", "foo-label");
            ConstantCategory cat = newConstCategory("CONST_CAT", target);
            List<ConstantCategory> cats = List.of(cat);

            // Access via public rebuilder — but getConstantByName is private. Use reflection.
            // 这里简化: 测 getVariableByName 是 public, getConstantByName 是 private; 同样 pattern
            // 通过 Variable 路径验证 (同 pattern), Constant 路径通过 getConstantByName 公共调用方间接覆盖
            // (略 — 测 4 method public interface 通过 Variable 测试 + 反射)
            // 实际: getConstantByName 是 private, 直接反射验证
            Object result = invokeGetConstantByName(cats, "CONST_CAT", "FOO");
            assertThat(result).isSameAs(target);
        }

        @Test
        @DisplayName("miss — 空 categories 抛 RuleException 含 category.name")
        void miss() {
            List<ConstantCategory> cats = new ArrayList<>();
            assertThatThrownBy(() -> invokeGetConstantByName(cats, "MISSING", "foo"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Constant")
                .hasMessageContaining("MISSING.foo");
        }

        @Test
        @DisplayName("miss — category 存在但 child name 不匹配抛 RuleException")
        void categoryHitChildMiss() {
            Constant other = newConstant("OTHER", "other-label");
            ConstantCategory cat = newConstCategory("CONST_CAT", other);
            List<ConstantCategory> cats = List.of(cat);

            assertThatThrownBy(() -> invokeGetConstantByName(cats, "CONST_CAT", "FOO"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("CONST_CAT.FOO");
        }

        @Test
        @DisplayName("多 category — 找的是第二个 category 的 child")
        void secondCategoryHit() {
            Constant target = newConstant("FOO", "foo-label");
            ConstantCategory cat1 = newConstCategory("CAT1", newConstant("OTHER", "other"));
            ConstantCategory cat2 = newConstCategory("CAT2", target);
            List<ConstantCategory> cats = List.of(cat1, cat2);

            Object result = invokeGetConstantByName(cats, "CAT2", "FOO");
            assertThat(result).isSameAs(target);
        }
    }

    // ====== getConstantByLabel ======

    @Nested
    @DisplayName("getConstantByLabel 路径")
    class ConstantByLabel {

        @Test
        @DisplayName("hit — 匹配 label 返回该 Constant")
        void hit() {
            Constant target = newConstant("FOO", "foo-label");
            ConstantCategory cat = newConstCategory("CONST_CAT", target);
            List<ConstantCategory> cats = List.of(cat);

            Object result = invokeGetConstantByLabel(cats, "CONST_CAT", "foo-label");
            assertThat(result).isSameAs(target);
        }

        @Test
        @DisplayName("miss — 空 categories 抛 RuleException 含 category.label")
        void miss() {
            List<ConstantCategory> cats = new ArrayList<>();
            assertThatThrownBy(() -> invokeGetConstantByLabel(cats, "MISSING", "foo"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Constant")
                .hasMessageContaining("MISSING.foo");
        }

        @Test
        @DisplayName("miss — name 匹配但 label 不匹配抛 RuleException (找的是 label)")
        void nameMatchLabelMiss() {
            Constant other = newConstant("FOO", "other-label");
            ConstantCategory cat = newConstCategory("CONST_CAT", other);
            List<ConstantCategory> cats = List.of(cat);

            assertThatThrownBy(() -> invokeGetConstantByLabel(cats, "CONST_CAT", "foo-label"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("CONST_CAT.foo-label");
        }
    }

    // ====== getVariableByName (public API, 已存在部分覆盖) ======

    @Nested
    @DisplayName("getVariableByName 路径")
    class VariableByName {

        @Test
        @DisplayName("hit — 单 category + 匹配 name 应返回该 Variable")
        void hit() {
            Variable target = newVariable("FOO", "foo-label");
            VariableCategory cat = newVarCategory("VAR_CAT", target);
            List<VariableCategory> cats = List.of(cat);

            Variable result = rebuilder.getVariableByName(cats, "VAR_CAT", "FOO", Map.of());
            assertThat(result).isSameAs(target);
        }

        @Test
        @DisplayName("hit + namedMap rename — category 被 rename 后用新名查找")
        void hitWithNamedMap() {
            Variable target = newVariable("FOO", "foo-label");
            VariableCategory cat = newVarCategory("NEW_NAME", target);
            List<VariableCategory> cats = List.of(cat);

            // 查找时用 OLD_NAME, namedMap 重命名为 NEW_NAME
            Variable result = rebuilder.getVariableByName(cats, "OLD_NAME", "FOO",
                Map.of("OLD_NAME", "NEW_NAME"));
            assertThat(result).isSameAs(target);
        }

        @Test
        @DisplayName("miss — 多 category 都不匹配抛 RuleException 含 category.name")
        void miss() {
            VariableCategory cat = newVarCategory("OTHER_CAT", newVariable("OTHER", "other"));
            List<VariableCategory> cats = List.of(cat);

            assertThatThrownBy(() -> rebuilder.getVariableByName(cats, "MISSING", "foo", Map.of()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Variable")
                .hasMessageContaining("MISSING.foo");
        }
    }

    // ====== getVariableByLabel ======

    @Nested
    @DisplayName("getVariableByLabel 路径")
    class VariableByLabel {

        @Test
        @DisplayName("hit — 匹配 label 返回该 Variable")
        void hit() {
            Variable target = newVariable("FOO", "foo-label");
            VariableCategory cat = newVarCategory("VAR_CAT", target);
            List<VariableCategory> cats = List.of(cat);

            Variable result = rebuilder.getVariableByLabel(cats, "VAR_CAT", "foo-label", Map.of());
            assertThat(result).isSameAs(target);
        }

        @Test
        @DisplayName("miss — 找 label 但只有 name 匹配抛 RuleException")
        void nameMatchLabelMiss() {
            Variable other = newVariable("FOO", "other-label");
            VariableCategory cat = newVarCategory("VAR_CAT", other);
            List<VariableCategory> cats = List.of(cat);

            assertThatThrownBy(() -> rebuilder.getVariableByLabel(cats, "VAR_CAT", "foo-label", Map.of()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("VAR_CAT.foo-label");
        }
    }

    // ====== Reflection helpers (private method access) ======

    private Object invokeGetConstantByName(List<ConstantCategory> cats, String category, String name) {
        try {
            java.lang.reflect.Method m = RulesRebuilder.class.getDeclaredMethod(
                "getConstantByName", List.class, String.class, String.class);
            m.setAccessible(true);
            return m.invoke(rebuilder, cats, category, name);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Unwrap reflection wrapper — throw the actual cause (RuleException etc.)
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Object invokeGetConstantByLabel(List<ConstantCategory> cats, String category, String label) {
        try {
            java.lang.reflect.Method m = RulesRebuilder.class.getDeclaredMethod(
                "getConstantByLabel", List.class, String.class, String.class);
            m.setAccessible(true);
            return m.invoke(rebuilder, cats, category, label);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new RuntimeException(cause);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
