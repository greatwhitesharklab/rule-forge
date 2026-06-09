package com.ruleforge.model.rule;
/**
 * @author Jacky.gao
 * @since 2015年3月10日
 */
public class ParameterValue extends AbstractValue {
	private String variableName;
	private String variableLabel;
	private ValueType valueType=ValueType.Parameter;
	@Override
	public ValueType getValueType() {
		return valueType;
	}

	public String getVariableName() {
		return variableName;
	}


	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}


	public String getVariableLabel() {
		return variableLabel;
	}


	public void setVariableLabel(String variableLabel) {
		this.variableLabel = variableLabel;
	}


	@Override
	public String getId() {
		String id="[P]参数."+variableLabel;
		if(arithmetic!=null){
			id+=arithmetic.getId();
		}
		return id;
	}
}
