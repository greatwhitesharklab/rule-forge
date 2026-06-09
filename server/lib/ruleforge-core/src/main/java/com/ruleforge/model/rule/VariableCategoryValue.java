package com.ruleforge.model.rule;
/**
 * @author Jacky.gao
 * @since 2015年2月28日
 */
public class VariableCategoryValue extends AbstractValue {
	private String variableCategory;
	private ValueType valueType=ValueType.VariableCategory;
	public VariableCategoryValue() {
	}
	public VariableCategoryValue(String variableCategory) {
		this.variableCategory=variableCategory;
	}
	
	@Override
	public ValueType getValueType() {
		return valueType;
	}

	public void setVariableCategory(String variableCategory) {
		this.variableCategory = variableCategory;
	}
	
	@Override
	public String getId() {
		String id="[变量对象]"+variableCategory;
		if(arithmetic!=null){
			id+=arithmetic.getId();
		}
		return id;
	}
	public String getVariableCategory() {
		return variableCategory;
	}
}
