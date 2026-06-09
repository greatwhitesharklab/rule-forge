package com.ruleforge.builder.table;

import java.util.Iterator;
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
        if ((conditions == null || conditions.size() == 0) && (joints == null || joints.size() == 0)) {
            return null;
        }
        Junction topJunction = null;
        if (conditions.size() == 1) {
            return newCriteria(col, conditions.get(0));
        } else {
            if (joint.getType().equals(JointType.and)) {
                topJunction = new And();
            } else {
                topJunction = new Or();
            }
            buildConditionsCriterion(conditions, topJunction, col);
            buildJointsCriterion(joints, col, topJunction);
            return topJunction;
        }
    }

    public Criterion buildCriterion(Cell cell, ComplexColumn col) {
        Joint joint = cell.getJoint();
        if (joint == null) {
            return null;
        } else {
            List<Condition> conditions = joint.getConditions();
            List<Joint> joints = joint.getJoints();
            if ((conditions == null || conditions.size() == 0) && (joints == null || joints.size() == 0)) {
                return null;
            } else {
                Junction topJunction = null;
                if (conditions.size() == 1) {
                    return this.newCriteria(cell, col, conditions.get(0));
                } else {
                    if (joint.getType().equals(JointType.and)) {
                        topJunction = new And();
                    } else {
                        topJunction = new Or();
                    }

                    this.buildConditionsCriterion(cell, conditions, topJunction, col);
                    this.buildJointsCriterion(cell, joints, col, topJunction);
                    return topJunction;
                }
            }
        }
    }

    private void buildJointsCriterion(List<Joint> joints, Column col, Junction parentJunction) {
        if (joints == null || joints.size() == 0) {
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
        if (joints != null && joints.size() != 0) {
            Iterator var5 = joints.iterator();

            while (var5.hasNext()) {
                Joint joint = (Joint) var5.next();
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
        if (conditions == null || conditions.size() == 0) {
            return;
        }
        for (Condition condition : conditions) {
            Criteria criteria = newCriteria(col, condition);
            junction.addCriterion(criteria);
        }
    }

    private void buildConditionsCriterion(Cell cell, List<Condition> conditions, Junction junction, ComplexColumn col) {
        if (conditions != null && conditions.size() != 0) {
            Iterator var5 = conditions.iterator();

            while (var5.hasNext()) {
                Condition condition = (Condition) var5.next();
                Criteria criteria = this.newCriteria(cell, col, condition);
                junction.addCriterion(criteria);
            }
        }
    }

    private Criteria newCriteria(Column col, Condition condition) {
        Criteria criteria = new Criteria();
        Left left = new Left();
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(col.getVariableCategory());
        part.setVariableName(col.getVariableName());
        part.setVariableLabel(col.getVariableLabel());
        part.setDatatype(col.getDatatype());
        left.setLeftPart(part);
        left.setType(LeftType.variable);
        criteria.setLeft(left);
        criteria.setOp(condition.getOp());
        criteria.setValue(condition.getValue());
        return criteria;
    }

    private Criteria newCriteria(Cell cell, ComplexColumn col, Condition condition) {
        Criteria criteria = new Criteria();
        Left left = new Left();
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(col.getVariableCategory());
        part.setVariableLabel(cell.getVariableLabel());
        part.setVariableName(cell.getVariableName());
        part.setDatatype(cell.getDatatype());
        left.setLeftPart(part);
        left.setType(LeftType.variable);
        criteria.setLeft(left);
        criteria.setOp(condition.getOp());
        criteria.setValue(condition.getValue());
        return criteria;
    }
}
