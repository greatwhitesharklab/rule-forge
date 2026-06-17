package com.ruleforge.model.rete.builder;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Act;
import com.ruleforge.model.library.variable.CategoryType;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.library.variable.VariableLibrary;
import com.ruleforge.model.rete.AndNode;
import com.ruleforge.model.rete.BaseReteNode;
import com.ruleforge.model.rete.CriteriaNode;
import com.ruleforge.model.rete.ReteNode;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V5.100.5 — {@link AndBuilder#buildCriterion} characterization test。
 *
 * <p>锁 V5.100.5 flatten (外层 {@code Iterator var7 + while(true){do{...}while(nodes==null)}}
 * find-non-null 状态机 → enhanced for + continue, terminal block 移到 for 之后) 的行为不变性。
 * 这是 V5.96 立的 skip 的收口 — AndBuilder.buildCriterion 是最后一个 decompiled outer state
 * machine (KnowledgeSessionImpl labeled loops 之外)。
 *
 * <p>rete 构造契约 (AndNode 只在跨 object-type join 时出现, 同 type 多 criteria 走 chain):
 * <ul>
 *   <li>1-criterion And → result = [CriteriaNode] (passthrough, 无 AndNode)</li>
 *   <li>2 同 type criteria (Foo.name + Foo.name) → result = [CriteriaNode] (chain, 无 AndNode —
 *       rete-sharing: 同 type criteria 链式串到同一 ObjectTypeNode 下, 不需 join)</li>
 *   <li>2 跨 type criteria (Foo.name + Bar.value) → result = [AndNode] (join 2 个 ObjectTypeNode)</li>
 *   <li>0-criterion And (criterions==null) → 抛 RuleException("...and] need one child at least.")</li>
 * </ul>
 *
 * <p><b>Why 直接调 buildCriterion (不走 ReteBuilder.buildRete)</b>: buildCriterion 的输出是
 * {@code List<BaseReteNode>}, 直接可断言 (类型 + 结构)。 子 criterion 是 flat {@link Criteria}
 * (非 nested And/Or), 走 {@code this.buildCriteria} 直调, 不需 ReteBuilder.criterionBuilders 装配。
 *
 * @see com.ruleforge.docs.notes.v51005-andbuilder-buildcriterion-outer-flatten V5.100.5 完整 doc
 * @since 5.100.5
 */
@DisplayName("V5.100.5 — AndBuilder.buildCriterion characterization (outer state machine flatten)")
class AndBuilderBuildCriterionTest {

    private AndBuilder builder;
    private BuildContext context;

    @BeforeEach
    void setUp() {
        builder = new AndBuilder();
        // ResourceLibrary 含 Foo + Bar 2 category (跨 type join 才触发 AndNode)
        VariableLibrary lib = new VariableLibrary();
        lib.addVariableCategory(category("Foo", Foo.class.getName(), "name"));
        lib.addVariableCategory(category("Bar", Bar.class.getName(), "value"));
        ResourceLibrary rl = new ResourceLibrary(Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());
        // 空 objectTypeNodes — buildObjectTypeNode 按需 new + cache
        context = new BuildContextImpl(rl, new ArrayList<>());
    }

    private static VariableCategory category(String name, String clazz, String varName) {
        VariableCategory cat = new VariableCategory();
        cat.setName(name);
        cat.setType(CategoryType.Clazz);
        cat.setClazz(clazz);
        Variable v = new Variable();
        v.setName(varName);
        v.setLabel(varName);
        v.setType(Datatype.String);
        v.setAct(Act.In);
        cat.setVariables(Collections.singletonList(v));
        return cat;
    }

    /** Build a Criteria: <category>.<varName> == value. */
    private static Criteria criteria(String category, String varName, String value) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(category);
        part.setVariableName(varName);
        part.setVariableLabel(varName);
        part.setDatatype(Datatype.String);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue();
        sv.setContent(value);
        c.setValue(sv);
        return c;
    }

    private static And andWith(Criteria... criteria) {
        And and = new And();
        for (Criteria c : criteria) {
            and.addCriterion(c);
        }
        return and;
    }

    // ─── 1-criterion And: passthrough (无 AndNode) ──────────────────────────

    @Nested
    @DisplayName("1-criterion And → result = [CriteriaNode] (passthrough, 无 AndNode)")
    class SingleCriterion {

        // Given: And with 1 criterion (Foo.name == "alice")
        // When:  buildCriterion(and, context)
        // Then:  result = [CriteriaNode], 无 AndNode (criterions.size()==1 走 passthrough 分支)
        @Test
        @DisplayName("1 criterion → result.size()==1, 是 CriteriaNode, 不是 AndNode")
        void singleCriterionReturnsCriteriaNodeOnly() {
            And and = andWith(criteria("Foo", "name", "alice"));

            List<BaseReteNode> result = builder.buildCriterion(and, context);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(CriteriaNode.class);
            assertThat(result.get(0)).isNotInstanceOf(AndNode.class);
        }
    }

    // ─── 同 type 2 criteria: chain (rete-sharing, 无 AndNode) ───────────────

    @Nested
    @DisplayName("同 type 2 criteria → result = [CriteriaNode] (chain, 无 AndNode)")
    class SameTypeChain {

        // Given: And with 2 criteria both on Foo.name (alice + bob)
        // When:  buildCriterion(and, context)
        // Then:  result = [CriteriaNode] — 同 type criteria 链式串到同一 ObjectTypeNode,
        //        不需 AndNode join (rete-sharing). V5.100.5 flatten 保留这个 sharing 行为.
        @Test
        @DisplayName("2 同 type criteria → result = [CriteriaNode] (chain), 不是 AndNode")
        void sameTypeTwoCriteriaChainNotJoin() {
            And and = andWith(criteria("Foo", "name", "alice"), criteria("Foo", "name", "bob"));

            List<BaseReteNode> result = builder.buildCriterion(and, context);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(CriteriaNode.class);
            assertThat(result.get(0)).isNotInstanceOf(AndNode.class);
        }
    }

    // ─── 跨 type 2 criteria: AndNode join ──────────────────────────────────

    @Nested
    @DisplayName("跨 type 2 criteria → result = [AndNode] (join 2 ObjectTypeNode)")
    class CrossTypeJoin {

        // Given: And with 2 criteria on different types (Foo.name==alice AND Bar.value==x)
        // When:  buildCriterion(and, context)
        // Then:  result = [AndNode] — 跨 type 必须 AndNode join
        //   (AndNode 是 join 节点, downstream 在 buildCriterion 阶段不 build, 所以
        //   andNode.getChildrenNodes() 可空 — 锁的是 AndNode 存在, 不是 children)
        @Test
        @DisplayName("跨 type 2 criteria → result = [AndNode]")
        void crossTypeTwoCriteriaBuildAndNode() {
            And and = andWith(criteria("Foo", "name", "alice"), criteria("Bar", "value", "x"));

            List<BaseReteNode> result = builder.buildCriterion(and, context);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(AndNode.class);
        }

        // Given: And with 3 criteria (Foo.name + Bar.value + Foo.name) — mix of types
        // When:  buildCriterion(and, context)
        // Then:  result = [AndNode] — state machine 跑 3 iter, 至少有跨 type join
        @Test
        @DisplayName("3 criteria (mix type) → result = [AndNode], state machine 跑 3 iter 不卡")
        void threeMixedCriteriaBuildAndNode() {
            And and = andWith(
                    criteria("Foo", "name", "a"),
                    criteria("Bar", "value", "b"),
                    criteria("Foo", "name", "c"));

            List<BaseReteNode> result = builder.buildCriterion(and, context);

            assertThat(result).hasSize(1);
            // 关键: state machine flatten 跑完 3 iter 不卡死 (原 while(true) 卡死风险),
            // 跨 type mix 仍产出 AndNode
            assertThat(result.get(0)).isInstanceOf(AndNode.class);
        }
    }

    // ─── 0-criterion / null-criterions And: 抛 RuleException ────────────────

    @Nested
    @DisplayName("0-criterion And (criterions==null) → 抛 RuleException")
    class ZeroCriteria {

        // Given: And with no addCriterion (getCriterions() == null)
        // When:  buildCriterion(and, context)
        // Then:  RuleException("Condition join node[and] need one child at least.")
        @Test
        @DisplayName("criterions == null (new And 无 addCriterion) → 抛 RuleException")
        void nullCriterionsThrowsRuleException() {
            And and = new And();  // getCriterions() == null

            assertThatThrownBy(() -> builder.buildCriterion(and, context))
                    .isInstanceOf(RuleException.class)
                    .hasMessageContaining("need one child at least");
        }
    }

    // ─── V5.100.5 flatten 行为保留: 多次调用稳定 ────────────────────────────

    @Nested
    @DisplayName("V5.100.5 flatten 行为保留: 多次调用结构稳定 (无 sticky state)")
    class FlattenPreservation {

        // Given: 2 个独立 And, 各 1 跨 type criterion pair
        // When:  buildCriterion 各跑 1 次
        // Then:  2 次结果都是 [AndNode], 结构一致 (无 sticky state 跨调用)
        @Test
        @DisplayName("2 次独立 buildCriterion 跨 type And → 都是 [AndNode], 结构稳定")
        void repeatedBuildStableStructure() {
            And and1 = andWith(criteria("Foo", "name", "alice"), criteria("Bar", "value", "x"));
            And and2 = andWith(criteria("Foo", "name", "alice"), criteria("Bar", "value", "x"));

            List<BaseReteNode> result1 = builder.buildCriterion(and1, context);
            List<BaseReteNode> result2 = builder.buildCriterion(and2, context);

            assertThat(result1).hasSize(1);
            assertThat(result2).hasSize(1);
            assertThat(result1.get(0)).isInstanceOf(AndNode.class);
            assertThat(result2.get(0)).isInstanceOf(AndNode.class);
        }

        // Given: And with 1 criterion
        // When:  buildCriterion → 结果是 CriteriaNode, 验证 criterions.size()==1 passthrough
        //   分支 (V5.100.5 terminal block 第 1 条件) 走通
        // Then:  result[0] 是 CriteriaNode
        @Test
        @DisplayName("1 criterion → passthrough 分支 (terminal block size==1 条件) 走通")
        void singleCriterionPassthroughBranch() {
            And and = andWith(criteria("Foo", "name", "solo"));

            List<BaseReteNode> result = builder.buildCriterion(and, context);

            // terminal block: criterions.size()==1 && currentCriteriaNode != null → return [criteriaNode]
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(CriteriaNode.class);
        }
    }

    /** Simple POJO for Foo category. */
    public static class Foo {
        private String name;
        public Foo() {}
        public Foo(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /** Simple POJO for Bar category (跨 type join 触发 AndNode). */
    public static class Bar {
        private String value;
        public Bar() {}
        public Bar(String value) { this.value = value; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
