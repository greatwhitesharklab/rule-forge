package com.ruleforge.model.rule.lhs;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.ruleforge.model.rule.Parameter;

/**
 * @author Jacky.gao
 * @since 2015年3月14日
 */
public class MethodLeftPart implements LeftPart{
	@JsonIgnore
	private String id;
	private String beanId;
	private String beanLabel;
	private String methodName;
	private String methodLabel;
	private List<Parameter> parameters;
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
	public String getMethodName() {
		return methodName;
	}
	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}
	public String getMethodLabel() {
		return methodLabel;
	}
	public void setMethodLabel(String methodLabel) {
		this.methodLabel = methodLabel;
	}
	public List<Parameter> getParameters() {
		return parameters;
	}
	public void setParameters(List<Parameter> parameters) {
		this.parameters = parameters;
	}
	@Override
	public String getId() {
		if(id==null){
			if(parameters!=null){
				String parametersId="";
				int i=0;
				for(Parameter parameter:parameters){
					if(i>0){
						parametersId+=",";
					}
					parametersId+=parameter.getId();
					i++;
				}
				id="[方法]"+beanLabel+"."+methodLabel+"("+parametersId+")";				
			}else{
				id="[方法]"+beanLabel+"."+methodLabel;								
			}
		}
		return id;
	}
}
