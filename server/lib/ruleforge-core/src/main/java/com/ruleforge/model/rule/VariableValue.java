package com.ruleforge.model.rule;

import com.ruleforge.model.library.Datatype;

/**
 * @author Jacky.gao
 * @since 2014年12月29日
 */
public class VariableValue extends AbstractValue{
	private String variableName;
	private String variableLabel;
	private String variableCategory;
	private Datatype datatype;
	private ValueType valueType=ValueType.Variable;
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
	public String getVariableCategory() {
		return variableCategory;
	}
	public void setVariableCategory(String variableCategory) {
		this.variableCategory = variableCategory;
	}
	public Datatype getDatatype() {
		return datatype;
	}
	public void setDatatype(Datatype datatype) {
		this.datatype = datatype;
	}
	public ValueType getValueType() {
		return valueType;
	}
	public String getId() {
		String id="[变量]"+variableCategory+"."+variableLabel;
		if(arithmetic!=null){
			id+=arithmetic.getId();
		}
		return id;
	}
}
