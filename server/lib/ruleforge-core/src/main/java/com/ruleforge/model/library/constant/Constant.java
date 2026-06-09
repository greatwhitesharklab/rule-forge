package com.ruleforge.model.library.constant;

import com.ruleforge.model.library.Datatype;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class Constant {
	private String name;
	private String label;
	private Datatype type;
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getLabel() {
		return label;
	}
	public void setLabel(String label) {
		this.label = label;
	}
	public Datatype getType() {
		return type;
	}
	public void setType(Datatype type) {
		this.type = type;
	}
}
