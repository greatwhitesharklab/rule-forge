package com.ruleforge.model.library.action;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * @since 2014年12月29日
 */
public class SpringBean {
	private String id;
	private String name;
	private List<Method> methods;
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<Method> getMethods() {
		return methods;
	}
	public void addMethod(Method method) {
		if(methods==null){
			methods=new ArrayList<Method>();
		}
		this.methods.add(method);
	}
	public void setMethods(List<Method> methods) {
		this.methods = methods;
	}
	
	
}
