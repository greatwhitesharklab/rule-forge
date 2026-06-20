package com.ruleforge.builder.table;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Junction;
import com.ruleforge.model.rule.lhs.Or;
import com.ruleforge.builder.table.CellRange;
import com.ruleforge.model.crosstab.ConditionCrossCell;
import com.ruleforge.model.table.Condition;
import com.ruleforge.model.table.Joint;
import com.ruleforge.model.table.JointType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.9.4 — {@link CrosstabRulesBuilder#buildCriterion(ConditionCrossCell, CellRange)}
 * 行为契约 BDD。
 *
 * <p>锁 V6.9.4 收口 (if/else state machine → early return + {@code isEmpty()} 风格统一)
 * 的行为不变性。跟 {@code CellContentBuilder.buildCriterion} 共享契约:
 * <ul>
 *   <li><b>joint == null</b>: 返 {@code null}</li>
 *   <li><b>conditions + joints 都空</b>: 返 {@code null}</li>
 *   <li><b>conditions.size() == 1</b>: passthrough → 返 {@code Criteria}</li>
 *   <li><b>conditions.size() > 1, type=and</b>: 返 {@link And}</li>
 *   <li><b>conditions.size() > 1, type=or</b>: 返 {@link Or}</li>
 * </ul>
 *
 * <p><b>Why V6.9.4 选这条</b>: 跟 CellContentBuilder L26-49 + L51-77 同档 Fernflower
 * 反编译 if/else state machine 收口,ConditionsCrossCell + CellRange 是 crosstab (复杂
 * 决策表) 路径。
 */
@DisplayName("V6.9.4 — CrosstabRulesBuilder.buildCriterion 行为契约")
class CrosstabRulesBuilderBuildCriterionTest {

    private CrosstabRulesBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new CrosstabRulesBuilder();
    }

    private static CellRange range() {
        CellRange r = new CellRange();
        r.setVariableCategory("Cat");
        r.setVariableName("var");
        r.setVariableLabel("label");
        r.setDatatype(Datatype.String);
        return r;
    }

    private static Condition condition() {
        Condition c = new Condition();
        Value v = new SimpleValue();
        ((SimpleValue) v).setContent("x");
        c.setValue(v);
        return c;
    }

    private static Joint joint(JointType type, int conditionCount) {
        Joint j = new Joint();
        j.setType(type);
        for (int i = 0; i < conditionCount; i++) {
            j.addCondition(condition());
        }
        return j;
    }

    @Nested
    @DisplayName("buildCriterion(ConditionCrossCell, CellRange)")
    class BuildCriterion {

        @Test
        @DisplayName("cell.getJoint() == null → 返 null")
        void nullJointReturnsNull() {
            ConditionCrossCell cell = new ConditionCrossCell();
            assertThat(builder.buildCriterion(cell, range())).isNull();
        }

        @Test
        @DisplayName("joint with no conditions, no joints → 返 null (V6.9.4 isEmpty 检查)")
        void emptyJointReturnsNull() {
            ConditionCrossCell cell = new ConditionCrossCell();
            Joint j = new Joint();
            j.setType(JointType.and);
            cell.setJoint(j);
            assertThat(builder.buildCriterion(cell, range())).isNull();
        }

        @Test
        @DisplayName("conditions.size() == 1 (passthrough) → 返 Criteria, 不走 Junction")
        void singleConditionReturnsCriteria() {
            ConditionCrossCell cell = new ConditionCrossCell();
            cell.setJoint(joint(JointType.and, 1));
            Criterion result = builder.buildCriterion(cell, range());
            assertThat(result).isInstanceOf(Criteria.class);
            assertThat(result).isNotInstanceOf(Junction.class);
        }

        @Test
        @DisplayName("conditions.size() > 1 + type=and → 返 And")
        void multipleConditionsAndReturnsAnd() {
            ConditionCrossCell cell = new ConditionCrossCell();
            cell.setJoint(joint(JointType.and, 2));
            assertThat(builder.buildCriterion(cell, range())).isInstanceOf(And.class);
        }

        @Test
        @DisplayName("conditions.size() > 1 + type=or → 返 Or")
        void multipleConditionsOrReturnsOr() {
            ConditionCrossCell cell = new ConditionCrossCell();
            cell.setJoint(joint(JointType.or, 3));
            assertThat(builder.buildCriterion(cell, range())).isInstanceOf(Or.class);
        }
    }
}