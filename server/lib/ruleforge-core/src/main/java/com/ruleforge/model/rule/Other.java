package com.ruleforge.model.rule;

import java.util.ArrayList;
import java.util.List;

import com.ruleforge.action.Action;

/**
 * @author Jacky.gao
 * @since 2016年5月4日
 */
public class Other {
	private List<Action> actions;

	public List<Action> getActions() {
		return actions;
	}
	
	public void setActions(List<Action> actions) {
		this.actions = actions;
	}
	
	public void addAction(Action action) {
		if(actions==null){
			actions=new ArrayList<Action>();
		}
		actions.add(action);
	}
}
