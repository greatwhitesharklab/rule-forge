package com.ruleforge.model.function.impl;

import java.math.BigDecimal;

import com.ruleforge.Utils;
import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.runtime.WorkingMemory;

/**
 * @author Jacky.gao
 * @since 2015年7月31日
 */
public class LnFunctionDescriptor implements FunctionDescriptor {
	private boolean disabled=false;
	@Override
	public Argument getArgument() {
		Argument arg=new Argument();
		arg.setName("对象");
		arg.setNeedProperty(true);
		return arg;
	}

	@Override
	public Object doFunction(Object object, String property,WorkingMemory workingMemory) {
		Object obj=Utils.getObjectProperty(object, property);
		BigDecimal bigobj=Utils.toBigDecimal(obj);
		return Math.log(bigobj.doubleValue());
	}

	@Override
	public String getName() {
		return "Ln";
	}

	@Override
	public String getLabel() {
		return "求自然对数";
	}

	@Override
	public boolean isDisabled() {
		return disabled;
	}
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
}
