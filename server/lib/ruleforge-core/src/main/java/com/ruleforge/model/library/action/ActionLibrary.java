package com.ruleforge.model.library.action;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Jacky.gao
 * @since 2014年12月29日
 */
public class ActionLibrary {
	private List<SpringBean> springBeans;
	public List<SpringBean> getSpringBeans() {
		return springBeans;
	}
	
	public void setSpringBeans(List<SpringBean> springBeans) {
		this.springBeans = springBeans;
	}

	public void addSpringBean(SpringBean springBean) {
		if(this.springBeans==null){
			this.springBeans=new ArrayList<SpringBean>();
		}
		this.springBeans.add(springBean);
	}
}
