package com.ruleforge.model.table;
/**
 * @author Jacky.gao
 * @since 2015年5月5日
 */
public class ScriptCell {
	private int row;
	private int col;
	private int rowspan;
	private String script;
	public int getRow() {
		return row;
	}
	public void setRow(int row) {
		this.row = row;
	}
	public int getCol() {
		return col;
	}
	public void setCol(int col) {
		this.col = col;
	}
	public int getRowspan() {
		return rowspan;
	}
	public void setRowspan(int rowspan) {
		this.rowspan = rowspan;
	}
	public String getScript() {
		return script;
	}
	public void setScript(String script) {
		this.script = script;
	}
}
