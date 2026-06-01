package com.ruleforge.model.rete;

import java.util.List;

import com.ruleforge.model.rule.lhs.BaseCriteria;

/**
 * @author Jacky.gao
 * @since 2016年8月17日
 */
public interface ConditionNode {
	String getCriteriaInfo();
	BaseCriteria getCriteria();
	List<ReteNode> getChildrenNodes();
	Line addLine(ReteNode toNode);
}
