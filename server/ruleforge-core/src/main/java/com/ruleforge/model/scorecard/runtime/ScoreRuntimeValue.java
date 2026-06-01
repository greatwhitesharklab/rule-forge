package com.ruleforge.model.scorecard.runtime;
/**
 * @author Jacky.gao
 * @since 2016年9月26日
 */
public class ScoreRuntimeValue {
	public static final String SCORE_VALUE="scoring_value";
	private int rowNumber;
	private String name;
	private String weight;
	private Object value;
	public ScoreRuntimeValue(int rowNumber,String name,String weight,Object value) {
		this.rowNumber=rowNumber;
		this.name = name;
		this.weight=weight;
		this.value=value;
	}
	public int getRowNumber() {
		return rowNumber;
	}
	public String getName() {
		return name;
	}
	
	public String getWeight() {
		return weight;
	}
	
	public Object getValue() {
		return value;
	}
}
