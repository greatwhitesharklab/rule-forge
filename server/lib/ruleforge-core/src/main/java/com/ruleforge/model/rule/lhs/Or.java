package com.ruleforge.model.rule.lhs;

/**
 * @author Jacky.gao
 * @since 2014年12月29日
 */
public class Or extends Junction {
	@Override
	public String getJunctionType() {
		return JunctionType.or.name();
	}
}
