package com.ruleforge.model.rule.lhs;

import java.util.List;

public class NamedJunction extends BaseCriterion{
	private String referenceName;
	private String variableCategory;
	private JunctionType junctionType;
	private List<NamedItem> items;
	public String getReferenceName() {
		return referenceName;
	}
	public void setReferenceName(String referenceName) {
		this.referenceName = referenceName;
	}
	public String getVariableCategory() {
		return variableCategory;
	}
	public void setVariableCategory(String variableCategory) {
		this.variableCategory = variableCategory;
	}
	public JunctionType getJunctionType() {
		return junctionType;
	}
	public void setJunctionType(JunctionType junctionType) {
		this.junctionType = junctionType;
	}
	public List<NamedItem> getItems() {
		return items;
	}
	public void setItems(List<NamedItem> items) {
		this.items = items;
	}
}
