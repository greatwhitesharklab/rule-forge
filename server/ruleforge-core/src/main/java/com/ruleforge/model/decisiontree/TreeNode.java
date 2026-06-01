package com.ruleforge.model.decisiontree;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @author Jacky.gao
 * @since 2016年2月26日
 */
public abstract class TreeNode {
	@JsonIgnore
	private TreeNode parentNode;
	private TreeNodeType nodeType;
	public void setParentNode(TreeNode parentNode) {
		this.parentNode = parentNode;
	}
	public TreeNode getParentNode() {
		return parentNode;
	}
	public TreeNodeType getNodeType() {
		return nodeType;
	}
	public void setNodeType(TreeNodeType nodeType) {
		this.nodeType = nodeType;
	}
}
