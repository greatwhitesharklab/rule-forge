package com.ruleforge.model.function.impl;

import com.ruleforge.exception.RuleException;
import com.ruleforge.Utils;
import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.runtime.WorkingMemory;

/**
 * @author Jacky.gao
 * @since 2015年7月30日
 */
public class StringLengthFunctionDescriptor implements FunctionDescriptor {
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
		if(obj==null){
			return 0;
		}else if(!(obj instanceof String)){
			throw new RuleException("Function[StringLength] parameter value must be String.");
		}
		return obj.toString().length();
	}

	@Override
	public String getName() {
		return "StringLength";
	}

	@Override
	public String getLabel() {
		return "计算字符长度";
	}

	@Override
	public boolean isDisabled() {
		return disabled;
	}
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
}
