package com.ruleforge.builder.table;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Junction;
import com.ruleforge.model.rule.lhs.Or;
import com.ruleforge.model.scorecard.ComplexColumn;
import com.ruleforge.model.table.Cell;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.Condition;
import com.ruleforge.model.table.Joint;
import com.ruleforge.model.table.JointType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.9.4 — {@link CellContentBuilder#buildCriterion} 行为契约 BDD。
 *
 * <p>锁 V6.9.4 收口 (if/else state machine → early return + {@code isEmpty()} 风格统一)
 * 的行为不变性。两个 {@code buildCriterion} overload 共享契约:
 * <ul>
 *   <li><b>joint == null</b>: 返回 {@code null}</li>
 *   <li><b>conditions + joints 都空</b>: 返回 {@code null}</li>
 *   <li><b>conditions.size() == 1</b>: passthrough → 返 {@code Criteria} (不走 Junction)</li>
 *   <li><b>conditions.size() > 1, type=and</b>: 返 {@link And}</li>
 *   <li><b>conditions.size() > 1, type=or</b>: 返 {@link Or}</li>
 * </ul>
 *
 * <p><b>Why V6.9.4 选这条</b>: 跟 V6.2-V6.4-V6.9.2-V6.9.3 同档 Fernflower 反编译
 * state machine 收口。CellContentBuilder L26-49 + L51-77 是 {@code if(conditions.size()==1)
 * {...} else {... build topJunction ...}} 3-branch state machine;CrosstabRulesBuilder L121-147
 * 同档。{@code size() == 0}/{@code size() != 0} → {@code isEmpty()}/{@code !isEmpty()}
 * 风格统一 (V5.x-V6.9 系列惯例)。
 */
@DisplayName("V6.9.4 — CellContentBuilder.buildCriterion 行为契约")
class CellContentBuilderTest {

    private CellContentBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new CellContentBuilder();
    }

    private static Column column() {
        Column col = new Column();
        col.setVariableCategory("Cat");
        col.setVariableName("var");
        col.setVariableLabel("label");
        col.setDatatype(Datatype.String);
        return col;
    }

    private static ComplexColumn complexColumn() {
        ComplexColumn col = new ComplexColumn();
        col.setVariableCategory("Cat");
        return col;
    }

    private static Cell cell() {
        Cell cell = new Cell();
        cell.setVariableLabel("label");
        cell.setVariableName("var");
        cell.setDatatype(Datatype.String);
        return cell;
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

    // ─── Column overload ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("buildCriterion(Cell, Column)")
    class ColumnOverload {

        @Test
        @DisplayName("joint == null → 返 null")
        void nullJointReturnsNull() {
            Cell cell = cell();
            assertThat(builder.buildCriterion(cell, column())).isNull();
        }

        @Test
        @DisplayName("joint with no conditions, no joints → 返 null (V6.9.4 isEmpty 检查)")
        void emptyJointReturnsNull() {
            Cell cell = cell();
            Joint j = new Joint();
            j.setType(JointType.and);
            cell.setJoint(j);
            assertThat(builder.buildCriterion(cell, column())).isNull();
        }

        @Test
        @DisplayName("conditions.size() == 1 (passthrough) → 返 Criteria, 不走 Junction")
        void singleConditionReturnsCriteria() {
            Cell cell = cell();
            cell.setJoint(joint(JointType.and, 1));
            Criterion result = builder.buildCriterion(cell, column());
            assertThat(result).isInstanceOf(Criteria.class);
            assertThat(result).isNotInstanceOf(Junction.class);
        }

        @Test
        @DisplayName("conditions.size() > 1 + type=and → 返 And")
        void multipleConditionsAndReturnsAnd() {
            Cell cell = cell();
            cell.setJoint(joint(JointType.and, 3));
            assertThat(builder.buildCriterion(cell, column())).isInstanceOf(And.class);
        }

        @Test
        @DisplayName("conditions.size() > 1 + type=or → 返 Or")
        void multipleConditionsOrReturnsOr() {
            Cell cell = cell();
            cell.setJoint(joint(JointType.or, 2));
            assertThat(builder.buildCriterion(cell, column())).isInstanceOf(Or.class);
        }
    }

    // ─── ComplexColumn overload ───────────────────────────────────────────────

    @Nested
    @DisplayName("buildCriterion(Cell, ComplexColumn)")
    class ComplexColumnOverload {

        @Test
        @DisplayName("joint == null → 返 null")
        void nullJointReturnsNull() {
            Cell cell = cell();
            assertThat(builder.buildCriterion(cell, complexColumn())).isNull();
        }

        @Test
        @DisplayName("joint with no conditions, no joints → 返 null")
        void emptyJointReturnsNull() {
            Cell cell = cell();
            Joint j = new Joint();
            j.setType(JointType.or);
            cell.setJoint(j);
            assertThat(builder.buildCriterion(cell, complexColumn())).isNull();
        }

        @Test
        @DisplayName("conditions.size() == 1 (passthrough) → 返 Criteria")
        void singleConditionReturnsCriteria() {
            Cell cell = cell();
            cell.setJoint(joint(JointType.and, 1));
            Criterion result = builder.buildCriterion(cell, complexColumn());
            assertThat(result).isInstanceOf(Criteria.class);
            assertThat(result).isNotInstanceOf(Junction.class);
        }

        @Test
        @DisplayName("conditions.size() > 1 + type=and → 返 And")
        void multipleConditionsAndReturnsAnd() {
            Cell cell = cell();
            cell.setJoint(joint(JointType.and, 2));
            assertThat(builder.buildCriterion(cell, complexColumn())).isInstanceOf(And.class);
        }

        @Test
        @DisplayName("conditions.size() > 1 + type=or → 返 Or")
        void multipleConditionsOrReturnsOr() {
            Cell cell = cell();
            cell.setJoint(joint(JointType.or, 3));
            assertThat(builder.buildCriterion(cell, complexColumn())).isInstanceOf(Or.class);
        }
    }

    // ─── size()==0 / size()!=0 → isEmpty() 风格 sanity check ─────────────────

    @Test
    @DisplayName("Collections.emptyList() 当 conditions → joint 应该返 null")
    void emptyListAsConditionsReturnsNull() {
        Cell cell = cell();
        Joint j = new Joint();
        j.setType(JointType.and);
        j.setConditions(Collections.emptyList());
        j.setJoints(Collections.emptyList());
        cell.setJoint(j);
        // 两个集合都空 → conditions.size()==0 && joints.size()==0 → null
        assertThat(builder.buildCriterion(cell, column())).isNull();
    }
}