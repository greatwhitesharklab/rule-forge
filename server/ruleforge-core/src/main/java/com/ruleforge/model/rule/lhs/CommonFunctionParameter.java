package com.ruleforge.model.rule.lhs;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.ruleforge.model.rule.Value;

/**
 * @author Jacky.gao
 * @since 2015年7月28日
 */
public class CommonFunctionParameter {
	@JsonIgnore
	private String id;
	private Value objectParameter;
	private String name;
	private String property;
	private String propertyLabel;
	public Value getObjectParameter() {
		return objectParameter;
	}
	public String getId(){
		if(id==null){
			id=objectParameter.getId();
			if(property!=null){
				id+=","+property;
			}
		}
		return id;
	}
	public void setObjectParameter(Value objectParameter) {
		this.objectParameter = objectParameter;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getProperty() {
		return property;
	}
	public void setProperty(String property) {
		this.property = property;
	}
	public String getPropertyLabel() {
		return propertyLabel;
	}
	public void setPropertyLabel(String propertyLabel) {
		this.propertyLabel = propertyLabel;
	}
}
