package com.ruleforge.model.library.constant;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * @since 2014年12月23日
 */
public class ConstantCategory {
	private String name;
	private String label;
	private List<Constant> constants;
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
	public List<Constant> getConstants() {
		return constants;
	}
	public void setConstants(List<Constant> constants) {
		this.constants = constants;
	}
	public void addConstant(Constant constant) {
		if(this.constants==null){
			this.constants=new ArrayList<Constant>();
		}
		this.constants.add(constant);
	}
}
