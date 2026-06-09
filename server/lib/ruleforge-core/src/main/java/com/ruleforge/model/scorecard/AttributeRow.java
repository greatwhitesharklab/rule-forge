package com.ruleforge.model.scorecard;

import java.util.List;

/**
 * @author Jacky.gao
 * @since 2016年9月21日
 */
public class AttributeRow extends CardRow{
	private List<ConditionRow> conditionRows;

	public List<ConditionRow> getConditionRows() {
		return conditionRows;
	}

	public void setConditionRows(List<ConditionRow> conditionRows) {
		this.conditionRows = conditionRows;
	}

}
