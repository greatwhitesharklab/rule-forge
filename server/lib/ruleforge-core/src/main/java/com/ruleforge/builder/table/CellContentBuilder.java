package com.ruleforge.builder.table;

import java.util.List;

import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Junction;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.Or;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.model.scorecard.ComplexColumn;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.table.Cell;
import com.ruleforge.model.table.Column;
import com.ruleforge.model.table.Condition;
import com.ruleforge.model.table.Joint;
import com.ruleforge.model.table.JointType;

/**
 * @author Jacky.gao
 * @author fred
 * 2015年1月20日
 */
public class CellContentBuilder {
    public Criterion buildCriterion(Cell cell, Column col) {
        Joint joint = cell.getJoint();
        if (joint == null) {
            return null;
        }
        List<Condition> conditions = joint.getConditions();
        List<Joint> joints = joint.getJoints();
        // V6.9.4 — size()==0 → isEmpty() 风格统一
        if (isEmpty(conditions) && isEmpty(joints)) {
            return null;
        }
        // V6.9.4 — 收口 if/else state machine: size==1 passthrough 提为 early return
        if (conditions.size() == 1) {
            return newCriteria(col, conditions.get(0));
        }
        Junction topJunction = topJunctionOf(joint);
        buildConditionsCriterion(conditions, topJunction, col);
        buildJointsCriterion(joints, col, topJunction);
        return topJunction;
    }

    public Criterion buildCriterion(Cell cell, ComplexColumn col) {
        Joint joint = cell.getJoint();
        if (joint == null) {
            return null;
        }
        List<Condition> conditions = joint.getConditions();
        List<Joint> joints = joint.getJoints();
        if (isEmpty(conditions) && isEmpty(joints)) {
            return null;
        }
        if (conditions.size() == 1) {
            return this.newCriteria(cell, col, conditions.get(0));
        }
        Junction topJunction = topJunctionOf(joint);
        this.buildConditionsCriterion(cell, conditions, topJunction, col);
        this.buildJointsCriterion(cell, joints, col, topJunction);
        return topJunction;
    }

    private static Junction topJunctionOf(Joint joint) {
        return joint.getType().equals(JointType.and) ? new And() : new Or();
    }

    private static <T> boolean isEmpty(List<T> list) {
        return list == null || list.isEmpty();
    }

    private void buildJointsCriterion(List<Joint> joints, Column col, Junction parentJunction) {
        if (joints == null || joints.isEmpty()) {
            return;
        }
        for (Joint joint : joints) {
            Junction junction = joint.getJunction();
            List<Condition> conditions = joint.getConditions();
            buildConditionsCriterion(conditions, junction, col);
            List<Joint> children = joint.getJoints();
            buildJointsCriterion(children, col, junction);
            parentJunction.addCriterion(junction);
        }
    }

    private void buildJointsCriterion(Cell cell, List<Joint> joints, ComplexColumn col, Junction parentJunction) {
        // V6.9.4 — size()!=0 → !isEmpty()
        if (joints != null && !joints.isEmpty()) {
            // V5.96 — Iterator var123 → enhanced for
            for (Joint joint : joints) {
                Junction junction = joint.getJunction();
                List<Condition> conditions = joint.getConditions();
                this.buildConditionsCriterion(cell, conditions, junction, col);
                List<Joint> children = joint.getJoints();
                this.buildJointsCriterion(cell, children, col, junction);
                parentJunction.addCriterion(junction);
            }
        }
    }

    private void buildConditionsCriterion(List<Condition> conditions, Junction junction, Column col) {
        // V6.9.4 — size()==0 → isEmpty()
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        for (Condition condition : conditions) {
            Criteria criteria = newCriteria(col, condition);
            junction.addCriterion(criteria);
        }
    }

    private void buildConditionsCriterion(Cell cell, List<Condition> conditions, Junction junction, ComplexColumn col) {
        // V6.9.4 — size()!=0 → !isEmpty()
        if (conditions != null && !conditions.isEmpty()) {
            // V5.96 — Iterator var123 → enhanced for
            for (Condition condition : conditions) {
                Criteria criteria = this.newCriteria(cell, col, condition);
                junction.addCriterion(criteria);
            }
        }
    }

    private Criteria newCriteria(Column col, Condition condition) {
        return buildCriteria(
            newVariableLeftPart(col.getVariableCategory(), col.getVariableName(),
                col.getVariableLabel(), col.getDatatype()),
            condition);
    }

    private Criteria newCriteria(Cell cell, ComplexColumn col, Condition condition) {
        return buildCriteria(
            newVariableLeftPart(col.getVariableCategory(), cell.getVariableName(),
                cell.getVariableLabel(), cell.getDatatype()),
            condition);
    }

    // V6.9.23 — V6.9.14 helper extract: 2 overloads 14 行 100% 同构 (build Left + VariableLeftPart +
    // Criteria skeleton + setOp/setValue) → 1 builder + 1 VariableLeftPart factory
    private VariableLeftPart newVariableLeftPart(String category, String name, String label, Datatype datatype) {
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(category);
        part.setVariableName(name);
        part.setVariableLabel(label);
        part.setDatatype(datatype);
        return part;
    }

    private Criteria buildCriteria(VariableLeftPart part, Condition condition) {
        Left left = new Left();
        left.setLeftPart(part);
        left.setType(LeftType.variable);
        Criteria criteria = new Criteria();
        criteria.setLeft(left);
        criteria.setOp(condition.getOp());
        criteria.setValue(condition.getValue());
        return criteria;
    }
}
