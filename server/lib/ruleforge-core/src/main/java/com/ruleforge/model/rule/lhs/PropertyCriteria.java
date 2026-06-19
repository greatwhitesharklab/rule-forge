package com.ruleforge.model.rule.lhs;

import java.util.List;

import com.ruleforge.Utils;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Value;
import com.ruleforge.engine.AssertorEvaluator;
import com.ruleforge.engine.EvaluationContext;

/**
 * @author Jacky.gao
 * 2015年6月1日
 */
public class PropertyCriteria {
    private String property;
    private Op op;
    private Value value;
    private String id;
    /**
     * V5.80 — 父 fact type 名(DRL {@code Type(field op value)} 的 Type)。
     * V5.78+ 路径下 {@code DrlDeserializer.toCriteria} 转 {@code VariableLeftPart}
     * 时用此字段 setVariableCategory(跟 {@code RulesRebuilder} NamedJunction
     * 路径对齐,见 RulesRebuilder.java line 152-154);老 PropertyCriteria.evaluate
     * 路径不用 factType(直接在 obj 上取 property),但 V5.80 强制 caller 设
     * 上来避免 V5.78 漏填回归。可空(老 caller 保留兼容),DRL 路径必须填。
     */
    private String factType;

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

    /** V5.80 — 父 fact type 名;DRL parser 必填,老 caller 可不填。 */
    public String getFactType() {
        return this.factType;
    }

    public void setFactType(String factType) {
        this.factType = factType;
    }
}
