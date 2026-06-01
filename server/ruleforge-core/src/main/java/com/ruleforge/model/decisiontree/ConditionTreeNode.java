package com.ruleforge.model.decisiontree;

import java.util.List;

import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Value;

/**
 * @author Jacky.gao
 * @since 2016年2月26日
 */
public class ConditionTreeNode extends TreeNode{
	private Value value;
	private Op op;
	private List<ConditionTreeNode> conditionTreeNodes;
	private List<VariableTreeNode> variableTreeNodes;
	private List<ActionTreeNode> actionTreeNodes;
	public Value getValue() {
		return value;
	}
	public void setValue(Value value) {
		this.value = value;
	}
	public Op getOp() {
		return op;
	}
	public void setOp(Op op) {
		this.op = op;
	}
	public List<ConditionTreeNode> getConditionTreeNodes() {
		return conditionTreeNodes;
	}
	public void setConditionTreeNodes(List<ConditionTreeNode> conditionTreeNodes) {
		this.conditionTreeNodes = conditionTreeNodes;
	}
	public List<VariableTreeNode> getVariableTreeNodes() {
		return variableTreeNodes;
	}
	public void setVariableTreeNodes(List<VariableTreeNode> variableTreeNodes) {
		this.variableTreeNodes = variableTreeNodes;
	}
	public List<ActionTreeNode> getActionTreeNodes() {
		return actionTreeNodes;
	}
	public void setActionTreeNodes(List<ActionTreeNode> actionTreeNodes) {
		this.actionTreeNodes = actionTreeNodes;
	}
}
