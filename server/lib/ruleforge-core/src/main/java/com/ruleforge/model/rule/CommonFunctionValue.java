package com.ruleforge.model.rule;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.ruleforge.model.rule.lhs.CommonFunctionParameter;

/**
 * @author Jacky.gao
 * @since 2015年7月28日
 */
public class CommonFunctionValue extends AbstractValue{
	@JsonIgnore
	private String id;
	private String name;
	private String label;
	private CommonFunctionParameter parameter;
	private ValueType valueType=ValueType.CommonFunction;
	@Override
	public String getId() {
		if(id==null){
			id= "[函数]"+label+"("+parameter.getId()+")";
			if(arithmetic!=null){
				id=id+arithmetic.getId();
			}
		}
		return id;
	}
	@Override
	public ValueType getValueType() {
		return valueType;
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
	public void setValueType(ValueType valueType) {
		this.valueType = valueType;
	}
	public CommonFunctionParameter getParameter() {
		return parameter;
	}
	public void setParameter(CommonFunctionParameter parameter) {
		this.parameter = parameter;
	}
}
