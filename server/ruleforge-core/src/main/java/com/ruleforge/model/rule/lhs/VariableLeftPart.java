package com.ruleforge.model.rule.lhs;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.ruleforge.model.library.Datatype;

/**
 * @author Jacky.gao
 * @since 2015年3月14日
 */
public class VariableLeftPart implements LeftPart{
	@JsonIgnore
	private String id;
	private String variableName;
	private String variableLabel;
	private String variableCategory;
	private Datatype datatype;
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
	@Override
	public String getId() {
		if(id==null){
			id="[变量]"+getVariableCategory()+"."+getVariableLabel();
		}
		return id;
	}
}
