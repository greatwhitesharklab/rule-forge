package com.ruleforge.model.rete.builder;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Act;
import com.ruleforge.model.library.variable.CategoryType;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.library.variable.VariableLibrary;
import com.ruleforge.model.rete.BaseReteNode;
import com.ruleforge.model.rete.CriteriaNode;
import com.ruleforge.model.rete.OrNode;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.Or;
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
 * V6.9.3 — {@link OrBuilder#buildCriterion} 行为契约 BDD。
 *
 * <p>锁 V6.9.3 收口 (Fernflower if/else if/else state machine → early return) 的行为不变性:
 * <ul>
 *   <li><b>1 child node</b>: result = [child] (passthrough, 无 OrNode)</li>
 *   <li><b>2+ child nodes</b>: result = [OrNode], children addLine 到 OrNode</li>
 *   <li><b>0-criterion</b>: 抛 RuleException</li>
 * </ul>
 *
 * <p><b>Why V6.9.3 选这条</b>: 跟 V6.2 AbstractActivity.visitPaths / V6.3 KnowledgeBase /
 * V6.4 LeftParser / V6.9.2 joinNodeIsPassed 同档 Fernflower 反编译 state machine 收口。
 * OrBuilder.buildCriterion L35-49 旧实现:
 * <pre>
 *   if (childNodes.size() == 0) return null;
 *   else if (childNodes.size() == 1) return childNodes;
 *   else { build OrNode + addLines + return [orNode]; }
 * </pre>
 * 收口成 early return + 单层 if,行为 100% 等价。AndBuilder.buildCriterion L83-98 同档,
 * 已有 V5.100.5 test 覆盖关键路径,本测试仅锁 OrBuilder 路径。
 */
@DisplayName("V6.9.3 — OrBuilder.buildCriterion 行为契约")
class OrBuilderBuildCriterionTest {

    private OrBuilder builder;
    private BuildContext context;

    @BeforeEach
    void setUp() {
        builder = new OrBuilder();
        // ResourceLibrary 含 Foo + Bar 2 category (跨 type Or 才触发 OrNode)
        VariableLibrary lib = new VariableLibrary();
        lib.addVariableCategory(category("Foo", "name"));
        lib.addVariableCategory(category("Bar", "value"));
        ResourceLibrary rl = new ResourceLibrary(Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());
        context = new BuildContextImpl(rl, new ArrayList<>());
    }

    private static VariableCategory category(String name, String varName) {
        VariableCategory cat = new VariableCategory();
        cat.setName(name);
        cat.setType(CategoryType.Clazz);
        cat.setClazz("com.ruleforge.model.GeneralEntity");
        Variable v = new Variable();
        v.setName(varName);
        v.setLabel(varName);
        v.setType(Datatype.String);
        v.setAct(Act.In);
        cat.setVariables(Collections.singletonList(v));
        return cat;
    }

    private static Criterion criteria(String categoryName, String varLabel, String value) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(categoryName);
        part.setVariableName(varLabel);
        part.setVariableLabel(varLabel);
        part.setDatatype(Datatype.String);
        left.setLeftPart(part);

        SimpleValue sv = new SimpleValue();
        sv.setContent(value);

        c.setLeft(left);
        c.setOp(Op.Equals);
        c.setValue(sv);
        return c;
    }

    private static Or orWith(Criterion... criterions) {
        Or or = new Or();
        for (Criterion c : criterions) {
            or.addCriterion(c);
        }
        return or;
    }

    // ─── 1-criterion Or: passthrough (无 OrNode) ──────────────────────────────

    @Nested
    @DisplayName("1-criterion Or → result = [CriteriaNode] (passthrough, 无 OrNode)")
    class SingleCriterion {

        // Given: Or with 1 criterion (Foo.name == "alice")
        // When:  buildCriterion(or, context)
        // Then:  result = [CriteriaNode], 无 OrNode (V6.9.3 early return size==1 path)
        @Test
        @DisplayName("1 criterion → result.size()==1, 是 CriteriaNode, 不是 OrNode")
        void singleCriterionReturnsCriteriaNodeOnly() {
            Or or = orWith(criteria("Foo", "name", "alice"));

            List<BaseReteNode> result = builder.buildCriterion(or, context);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(CriteriaNode.class);
            assertThat(result.get(0)).isNotInstanceOf(OrNode.class);
        }
    }

    // ─── 2+ criterion Or: OrNode join ─────────────────────────────────────────

    @Nested
    @DisplayName("2+ criterion Or → result = [OrNode] (join 2+ CriteriaNode)")
    class MultiCriteria {

        // Given: Or with 2 criteria (Foo.name == alice OR Bar.value == x)
        // When:  buildCriterion(or, context)
        // Then:  result = [OrNode]
        @Test
        @DisplayName("2 criteria (跨 type) → result = [OrNode]")
        void twoCriteriaBuildOrNode() {
            Or or = orWith(
                criteria("Foo", "name", "alice"),
                criteria("Bar", "value", "x"));

            List<BaseReteNode> result = builder.buildCriterion(or, context);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(OrNode.class);
        }

        // Given: Or with 3 criteria (mix type)
        // When:  buildCriterion(or, context)
        // Then:  result = [OrNode]
        @Test
        @DisplayName("3 criteria (mix type) → result = [OrNode]")
        void threeCriteriaBuildOrNode() {
            Or or = orWith(
                criteria("Foo", "name", "a"),
                criteria("Bar", "value", "b"),
                criteria("Foo", "name", "c"));

            List<BaseReteNode> result = builder.buildCriterion(or, context);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(OrNode.class);
        }
    }

    // ─── 0-criterion Or: 抛 RuleException ─────────────────────────────────────

    @Nested
    @DisplayName("0-criterion Or (criterions==null) → 抛 RuleException")
    class ZeroCriteria {

        // Given: Or with no addCriterion
        // When:  buildCriterion(or, context)
        // Then:  RuleException
        @Test
        @DisplayName("criterions == null → 抛 RuleException")
        void nullCriterionsThrowsRuleException() {
            Or or = new Or();
            assertThatThrownBy(() -> builder.buildCriterion(or, context))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Condition join node[or] need one child at least");
        }
    }
}