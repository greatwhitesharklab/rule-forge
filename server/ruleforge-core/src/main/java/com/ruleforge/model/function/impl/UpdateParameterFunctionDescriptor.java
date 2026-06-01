package com.ruleforge.model.function.impl;

import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.runtime.WorkingMemory;

/**
 * @author Jacky.gao
 * @since 2015年7月31日
 */
public class UpdateParameterFunctionDescriptor implements FunctionDescriptor {
	@Override
	public Argument getArgument() {
		return null;
	}

	@Override
	public Object doFunction(Object object, String property,WorkingMemory workingMemory) {
		return workingMemory.update(workingMemory.getParameters());
	}

	@Override
	public String getName() {
		return "UpdateParameter";
	}

	@Override
	public String getLabel() {
		return "更新参数";
	}

	@Override
	public boolean isDisabled() {
		return false;
	}
}
