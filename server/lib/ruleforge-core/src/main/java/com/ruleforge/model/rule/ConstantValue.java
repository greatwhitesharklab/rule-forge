package com.ruleforge.model.rule;
/**
 * @author Jacky.gao
 * @since 2014年12月29日
 */
public class ConstantValue extends AbstractValue{
	private String constantName;
	private String constantLabel;
	private String constantCategory;
	private ValueType valueType=ValueType.Constant;
	public String getConstantName() {
		return constantName;
	}
	public void setConstantName(String constantName) {
		this.constantName = constantName;
	}
	public String getConstantLabel() {
		return constantLabel;
	}
	public void setConstantLabel(String constantLabel) {
		this.constantLabel = constantLabel;
	}
	public String getConstantCategory() {
		return constantCategory;
	}
	public void setConstantCategory(String constantCategory) {
		this.constantCategory = constantCategory;
	}
	public ValueType getValueType() {
		return valueType;
	}
	public String getId() {
		String id="[常量]"+constantCategory+"."+constantLabel;
		if(arithmetic!=null){
			id+=arithmetic.getId();
		}
		return id;
	}
}
