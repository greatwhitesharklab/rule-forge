package com.ruleforge.action;

import com.ruleforge.Utils;
import com.ruleforge.debug.MsgType;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.engine.Context;
import com.ruleforge.engine.ValueCompute;
import org.apache.commons.lang.StringUtils;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.util.HashMap;
import java.util.List;


public class VariableAssignAction extends AbstractAction {
    private String referenceName;
    private String variableName;
    private String variableLabel;
    private String variableCategory;
    private Datatype datatype;
    private Value value;
    private LeftType type;
    private ActionType actionType;

    public VariableAssignAction() {
        this.actionType = ActionType.VariableAssign;
    }

    public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
        if (this.value == null) {
            return null;
        }
        // V6.9.6 — 收口 3-level nested if/else Fernflower 反编译 state machine (V6.2-V6.9.5 同档):
        //   if (value == null) return null;
        //   else {
        //       ... compute className / targetFact ...
        //       if (targetFact == null) throw;
        //       else {
        //           ... compute label ...
        //           if (Enum && obj != null && notBlank) { enum path }
        //           else if (obj != null) { generic convert }
        //           // implicit else: skip conversion
        //           setObjectProperty + logMsg;
        //       }
        //   }
        // 收口成 early return chain, 行为 100% 等价。
        ValueCompute valueCompute = context.getValueCompute();
        Object obj = valueCompute.complexValueCompute(this.value, matchedObject, context, allMatchedObjects);
        String className = context.getVariableCategoryClass(this.variableCategory);
        Object targetFact = className.equals(HashMap.class.getName())
            ? context.getWorkingMemory().getParameters()
            : valueCompute.findObject(className, matchedObject, context);
        if (targetFact == null) {
            throw new RuleException("Class[" + className + "] not found in working memory.");
        }
        String label = this.variableCategory + "." + (this.variableLabel == null ? this.variableName : this.variableLabel);
        if (this.datatype.equals(Datatype.Enum) && obj != null && StringUtils.isNotBlank(obj.toString())) {
            PropertyDescriptor pd;
            try {
                pd = new PropertyDescriptor(this.variableName, targetFact.getClass());
            } catch (IntrospectionException e) {
                throw new RuleException(label, obj, "Property not found: " + this.variableName, e);
            }
            Class<Enum> targetClass = (Class<Enum>) pd.getPropertyType();
            obj = Enum.valueOf(targetClass, obj.toString());
        } else if (obj != null) {
            try {
                obj = this.datatype.convert(obj);
            } catch (IllegalArgumentException e) {
                throw new RuleException(label, obj, e.getMessage(), e);
            }
        }

        String propertyName = this.variableName;
        Utils.setObjectProperty(targetFact, propertyName, obj);

        // 执行信息
        String msg = "### 变量赋值：" + label + "=" + obj;
        context.logMsg(msg, MsgType.VarAssign);

        return null;
    }

    public LeftType getType() {
        return this.type;
    }

    public void setType(LeftType type) {
        this.type = type;
    }

    public String getReferenceName() {
        return this.referenceName;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

    public String getVariableName() {
        return this.variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getVariableLabel() {
        return this.variableLabel;
    }

    public void setVariableLabel(String variableLabel) {
        this.variableLabel = variableLabel;
    }

    public String getVariableCategory() {
        return this.variableCategory;
    }

    public void setVariableCategory(String variableCategory) {
        this.variableCategory = variableCategory;
    }

    public Value getValue() {
        return this.value;
    }

    public void setValue(Value value) {
        this.value = value;
    }

    public Datatype getDatatype() {
        return this.datatype;
    }

    public void setDatatype(Datatype datatype) {
        this.datatype = datatype;
    }

    public ActionType getActionType() {
        return this.actionType;
    }
}
