package com.ruleforge.model.rule.lhs;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.ruleforge.model.rule.Parameter;

/**
 * @author Jacky.gao
 * @since 2015年3月14日
 */
public class FunctionLeftPart implements LeftPart{
	@JsonIgnore
	private String id;
	private String name;
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
				id = "[函数]."+name+"("+parametersId+")";				
			}else{
				id = "[函数]."+name;								
			}
		}
		return id;
	}
}
