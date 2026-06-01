package com.ruleforge.model.decisiontree;

import java.util.List;

import com.ruleforge.model.rule.lhs.Left;

/**
 * @author Jacky.gao
 * @since 2016年2月26日
 */
public class VariableTreeNode extends TreeNode{
	private Left left;
	private List<ConditionTreeNode> conditionTreeNodes;
	public Left getLeft() {
		return left;
	}
	public void setLeft(Left left) {
		this.left = left;
	}
	public List<ConditionTreeNode> getConditionTreeNodes() {
		return conditionTreeNodes;
	}
	public void setConditionTreeNodes(List<ConditionTreeNode> conditionTreeNodes) {
		this.conditionTreeNodes = conditionTreeNodes;
	}
}
