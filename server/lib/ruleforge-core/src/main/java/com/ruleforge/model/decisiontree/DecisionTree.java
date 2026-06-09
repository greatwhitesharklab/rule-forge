package com.ruleforge.model.decisiontree;

import java.util.Date;
import java.util.List;

import com.ruleforge.model.rule.Library;

/**
 * @author Jacky.gao
 * @since 2016年2月26日
 */
public class DecisionTree {
	private Integer salience;
	private Date effectiveDate;
	private Date expiresDate;
	private Boolean enabled;
	private Boolean debug;
	private String remark;
	private List<Library> libraries;
	private VariableTreeNode variableTreeNode;
	
	public Integer getSalience() {
		return salience;
	}
	public void setSalience(Integer salience) {
		this.salience = salience;
	}
	public Date getEffectiveDate() {
		return effectiveDate;
	}
	public void setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}
	public Date getExpiresDate() {
		return expiresDate;
	}
	public void setExpiresDate(Date expiresDate) {
		this.expiresDate = expiresDate;
	}
	public Boolean getEnabled() {
		return enabled;
	}
	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}
	public Boolean getDebug() {
		return debug;
	}
	public void setDebug(Boolean debug) {
		this.debug = debug;
	}
	public String getRemark() {
		return remark;
	}
	public void setRemark(String remark) {
		this.remark = remark;
	}
	public List<Library> getLibraries() {
		return libraries;
	}
	public void setLibraries(List<Library> libraries) {
		this.libraries = libraries;
	}
	public VariableTreeNode getVariableTreeNode() {
		return variableTreeNode;
	}
	public void setVariableTreeNode(VariableTreeNode variableTreeNode) {
		this.variableTreeNode = variableTreeNode;
	}
}
