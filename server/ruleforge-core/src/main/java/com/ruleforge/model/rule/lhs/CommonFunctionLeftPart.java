package com.ruleforge.model.rule.lhs;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.ruleforge.Utils;
import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.rule.Value;
import com.ruleforge.runtime.rete.EvaluationContext;


/**
 * @author Jacky.gao
 * @since 2015年7月28日
 */
public class CommonFunctionLeftPart implements LeftPart {
    @JsonIgnore
    private String id;
    private String name;
    private String label;
    private CommonFunctionParameter parameter;

    public Object evaluate(EvaluationContext context, Object obj, List<Object> allMatchedObjects) {
        FunctionDescriptor function = Utils.findFunctionDescriptor(name);
        Value value = parameter.getObjectParameter();
        Object object = context.getValueCompute().complexValueCompute(value, obj, context, allMatchedObjects);
        Argument arg = function.getArgument();
        String property = null;
        if (arg.isNeedProperty()) {
            property = parameter.getProperty();
        }
        return function.doFunction(object, property, context.getWorkingMemory());
    }

    @Override
    public String getId() {
        if (id == null) {
            id = label + "(" + parameter.getId() + ")";
        }
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public CommonFunctionParameter getParameter() {
        return parameter;
    }

    public void setParameter(CommonFunctionParameter parameter) {
        this.parameter = parameter;
    }
}
