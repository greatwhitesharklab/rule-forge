package com.ruleforge.model.library.action;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * @since 2014年12月29日
 */
public class Method {
	private String name;
	private String methodName;
	private List<Parameter> parameters;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<Parameter> getParameters() {
		return parameters;
	}
	public String getMethodName() {
		return methodName;
	}
	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}
	public void addParameter(Parameter parameter) {
		if(parameters==null){
			parameters=new ArrayList<Parameter>();
		}
		parameters.add(parameter);
	}
	public void setParameters(List<Parameter> parameters) {
		this.parameters = parameters;
	}
	
	
}
