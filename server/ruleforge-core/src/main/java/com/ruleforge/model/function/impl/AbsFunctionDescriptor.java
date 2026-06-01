package com.ruleforge.model.function.impl;

import com.ruleforge.Utils;
import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.runtime.WorkingMemory;

import java.math.BigDecimal;


/**
 * @author Jacky.gao
 * 2015年7月22日
 */
public class AbsFunctionDescriptor implements FunctionDescriptor {
    private boolean disabled = false;

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public String getLabel() {
        return "求绝对值";
    }

    @Override
    public String getName() {
        return "Abs";
    }

    @Override
    public Object doFunction(Object object, String property, WorkingMemory workingMemory) {
        Object value = Utils.getObjectProperty(object, property);
        BigDecimal bigvalue = Utils.toBigDecimal(value);
        return Math.abs(bigvalue.doubleValue());
    }

    @Override
    public Argument getArgument() {
        Argument p = new Argument();
        p.setName("对象");
        p.setNeedProperty(true);
        return p;
    }
}
