package com.ruleforge.model.rule.loop;

import java.util.List;

import com.ruleforge.action.Action;

/**
 * @author Jacky.gao
 * @since 2016年5月31日
 */
public class LoopEnd {
	private List<Action> actions;

	public List<Action> getActions() {
		return actions;
	}
	
	public void setActions(List<Action> actions) {
		this.actions = actions;
	}
}
