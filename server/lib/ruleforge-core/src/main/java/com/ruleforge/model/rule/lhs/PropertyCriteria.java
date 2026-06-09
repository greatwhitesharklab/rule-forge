package com.ruleforge.model.rule.lhs;

import java.util.List;

import com.ruleforge.Utils;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Value;
import com.ruleforge.runtime.assertor.AssertorEvaluator;
import com.ruleforge.runtime.rete.EvaluationContext;

/**
 * @author Jacky.gao
 * 2015年6月1日
 */
public class PropertyCriteria {
    private String property;
    private Op op;
    private Value value;
    private String id;

    public PropertyCriteria() {
    }

    public String getId() {
        if (this.id == null) {
            this.id = this.property + this.op.name() + this.value.getId();
        }

        return this.id;
    }

    public boolean evaluate(EvaluationContext context, Object obj, List<Object> allMatchedObjects) {
        Object left = Utils.getObjectProperty(obj, this.property);
        Object right = context.getValueCompute().complexValueCompute(this.value, obj, context, allMatchedObjects);
        if (right == null) {
            return false;
        } else {
            AssertorEvaluator assertorEvaluator = context.getAssertorEvaluator();
            Datatype datatype = Utils.getDatatype(left);
            boolean result = assertorEvaluator.evaluate(left, right, datatype, this.op);
            return result;
        }
    }

    public String getProperty() {
        return this.property;
    }

    public void setProperty(String property) {
        this.property = property;
    }

    public Op getOp() {
        return this.op;
    }

    public void setOp(Op op) {
        this.op = op;
    }

    public Value getValue() {
        return this.value;
    }

    public void setValue(Value value) {
        this.value = value;
    }
}
