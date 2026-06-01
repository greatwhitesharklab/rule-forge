package com.ruleforge.model.rule.lhs;

import java.util.List;

/**
 * @author Jacky.gao
 * @since 2015年6月1日
 */
public class ExprValue {
	private int total=0;
	private int match=0;
	private int notMatch=0;
	private List<Object> facts;
	public int getTotal() {
		return total;
	}
	public void setTotal(int total) {
		this.total = total;
	}
	public int getMatch() {
		return match;
	}
	public void setMatch(int match) {
		this.match = match;
	}
	public int getNotMatch() {
		return notMatch;
	}
	public void setNotMatch(int notMatch) {
		this.notMatch = notMatch;
	}
	public List<Object> getFacts() {
		return facts;
	}
	public void setFacts(List<Object> facts) {
		this.facts = facts;
	}
}
