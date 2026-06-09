package com.ruleforge.model.function.impl;

import java.math.BigDecimal;
import java.util.Collection;

import com.ruleforge.exception.RuleException;
import com.ruleforge.Utils;
import com.ruleforge.model.function.Argument;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.runtime.WorkingMemory;


/**
 * @author Jacky.gao
 * @since 2015年7月22日
 */
public class AvgFunctionDescriptor implements FunctionDescriptor{
	private boolean disabled=false;
	
	public boolean isDisabled() {
		return disabled;
	}
	public void setDisabled(boolean disabled) {
		this.disabled = disabled;
	}
	@Override
	public String getName() {
		return "Avg";
	}
	@Override
	public String getLabel() {
		return "求平均值";
	}
	@Override
	public Object doFunction(Object object, String property,WorkingMemory workingMemory) {
		Collection<?> list=null;
		if(object instanceof Collection){
			list=(Collection<?>)object;
		}else{
			throw new RuleException("Function[avg] parameter must be java.util.Collection type.");
		}
		BigDecimal total=null;
		for(Object obj:list){
			Object pvalue=Utils.getObjectProperty(obj, property);
			BigDecimal a=Utils.toBigDecimal(pvalue);
			if(total==null){
				total=a;
			}else{
				total=total.add(a);
			}
		}
		BigDecimal avgvalue=total.divide(new BigDecimal(list.size()),12,BigDecimal.ROUND_HALF_UP);
		return avgvalue.doubleValue();
	}
	@Override
	public Argument getArgument() {
		Argument p=new Argument();
		p.setName("集合对象");
		p.setNeedProperty(true);
		return p;
	}
}
