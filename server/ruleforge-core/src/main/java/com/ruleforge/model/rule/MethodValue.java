package com.ruleforge.model.rule;

import java.util.List;

/**
 * @author Jacky.gao
 * @since 2015年2月28日
 */
public class MethodValue extends AbstractValue {
	private String beanId;
	private String beanLabel;
	private String methodLabel;
	private String methodName;
	private List<Parameter> parameters;
	private ValueType valueType=ValueType.Method;
	@Override
	public ValueType getValueType() {
		return valueType;
	}

	@Override
	public String getId() {
		String id="[BEAN]["+beanId+"."+methodName+"]";
		if(arithmetic!=null){
			id+=arithmetic.getId();
		}
		return id;
	}

	public String getBeanId() {
		return beanId;
	}

	public void setBeanId(String beanId) {
		this.beanId = beanId;
	}

	public String getBeanLabel() {
		return beanLabel;
	}

	public void setBeanLabel(String beanLabel) {
		this.beanLabel = beanLabel;
	}

	public String getMethodLabel() {
		return methodLabel;
	}

	public void setMethodLabel(String methodLabel) {
		this.methodLabel = methodLabel;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	public List<Parameter> getParameters() {
		return parameters;
	}

	public void setParameters(List<Parameter> parameters) {
		this.parameters = parameters;
	}
}
