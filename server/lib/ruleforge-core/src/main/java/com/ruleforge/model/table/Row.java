package com.ruleforge.model.table;
/**
 * @author Jacky.gao
 * @since 2015年1月19日
 */
public class Row implements Comparable<Row>{
	private int num;
	private int height;
	public int getNum() {
		return num;
	}
	public void setNum(int num) {
		this.num = num;
	}
	public int getHeight() {
		return height;
	}
	public void setHeight(int height) {
		this.height = height;
	}
	public int compareTo(Row o) {
		return o.getNum()-num;
	}
}
