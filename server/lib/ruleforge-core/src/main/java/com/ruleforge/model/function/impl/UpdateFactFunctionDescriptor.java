package com.ruleforge.model.function.impl;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.runtime.WorkingMemory;

/**
 * @author Jacky.gao
 * @since 2015年7月31日
 */
public class UpdateFactFunctionDescriptor implements FunctionDescriptor {
	private boolean disabled=false;
	@Override
	public Argument getArgument() {
		Argument arg=new Argument();
		arg.setName("要更新的对象");
		return arg;
	}

	@Override
	public Object doFunction(Object object, String property,WorkingMemory workingMemory) {
		if(object instanceof String){
			String text=(String)object;
			if(text.equals("参数") || text.equals("parameter")){
				return workingMemory.update(workingMemory.getParameters());
			}else{
				throw new RuleException("Unsupport parameter["+text+"].");
			}
		}else{
			return workingMemory.update(object);
		}
	}

	@Override
	public String getName() {
		return "UpdateFact";
	}

	@Override
	public String getLabel() {
		return "更新工作区对象";
	}

	@Override
	public boolean isDisabled() {
		return disabled;
	}
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
}
