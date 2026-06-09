package com.ruleforge.model.library.action;

import com.ruleforge.model.library.Datatype;

/**
 * @author Jacky.gao
 * @since 2014年12月29日
 */
public class Parameter {
	private String name;
	private Datatype type;

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
}
