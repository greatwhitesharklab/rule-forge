package com.ruleforge.model.scorecard.runtime;
/**
 * @author Jacky.gao
 * @since 2016年9月26日
 */
public class CellItem {
	private String colName;
	private Object value;
	public CellItem(String colName,Object value) {
		this.colName=colName;
		this.value=value;
	}
	public String getColName() {
		return colName;
	}
	public Object getValue() {
		return value;
	}
}
