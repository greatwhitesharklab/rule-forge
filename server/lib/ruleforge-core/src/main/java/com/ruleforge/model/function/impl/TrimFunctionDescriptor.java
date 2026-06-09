package com.ruleforge.model.function.impl;

import com.ruleforge.Utils;
import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.runtime.WorkingMemory;


/**
 * @author Jacky.gao
 * @since 2015年7月22日
 */
public class TrimFunctionDescriptor implements FunctionDescriptor{
	private boolean disabled=false;
	
	public boolean isDisabled() {
		return disabled;
	}
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
	@Override
	public String getLabel() {
		return "字符去空格";
	}
	@Override
	public String getName() {
		return "Trim";
	}
	@Override
	public Object doFunction(Object object, String property,WorkingMemory workingMemory) {
		Object value=Utils.getObjectProperty(object, property);
		if(value==null){
			return "null";
		}
		return value.toString().trim();
	}
	@Override
	public Argument getArgument() {
		Argument p=new Argument();
		p.setName("对象");
		p.setNeedProperty(true);
		return p;
	}
}
