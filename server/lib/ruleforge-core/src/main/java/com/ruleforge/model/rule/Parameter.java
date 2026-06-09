package com.ruleforge.model.rule;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.ruleforge.exception.RuleException;
import com.ruleforge.model.library.Datatype;

/**
 * @author Jacky.gao
 * @since 2014年12月31日
 */
public class Parameter {
	@JsonIgnore
	private String id;
	private String name;
	private Datatype type;
	private Value value;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Datatype getType() {
		return type;
	}
	public void setType(Datatype type) {
		this.type = type;
	}
	public Value getValue() {
		return value;
	}
	public void setValue(Value value) {
		this.value = value;
	}
	public String getId() {
		if(id==null){
			if(value==null){
				throw new RuleException("Parameter ["+name+"] not assignment value.");
			}
			id=value.getId();
		}
		return id;
	}
}
