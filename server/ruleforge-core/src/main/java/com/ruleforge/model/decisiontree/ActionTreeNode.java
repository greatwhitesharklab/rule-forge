package com.ruleforge.model.decisiontree;

import java.util.List;

import com.ruleforge.action.Action;

/**
 * @author Jacky.gao
 * @since 2016年2月26日
 */
public class ActionTreeNode extends TreeNode{
	private List<Action> actions;

	public List<Action> getActions() {
		return actions;
	}

	public void setActions(List<Action> actions) {
		this.actions = actions;
	}
}
