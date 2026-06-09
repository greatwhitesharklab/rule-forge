package com.ruleforge.model.scorecard;

import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.table.Joint;

/**
 * @author Jacky.gao
 * @since 2016年9月20日
 */
public class CardCell implements Comparable<CardCell>{
	private String variableLabel;
	private String variableName;
	private String variableCategory;
	private Datatype datatype;
	private CellType type;
	private String weight;
	private Joint joint;
	private Value value;
	private int row;
	private int col;
	public String getVariableLabel() {
		return variableLabel;
	}
	public void setVariableLabel(String variableLabel) {
		this.variableLabel = variableLabel;
	}
	public String getVariableName() {
		return variableName;
	}
	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}
	public String getVariableCategory() {
		return variableCategory;
	}
	public void setVariableCategory(String variableCategory) {
		this.variableCategory = variableCategory;
	}
	public Datatype getDatatype() {
		return datatype;
	}
	public void setDatatype(Datatype datatype) {
		this.datatype = datatype;
	}
	public CellType getType() {
		return type;
	}
	public void setType(CellType type) {
		this.type = type;
	}
	public String getWeight() {
		return weight;
	}
	public void setWeight(String weight) {
		this.weight = weight;
	}
	public Joint getJoint() {
		return joint;
	}
	public void setJoint(Joint joint) {
		this.joint = joint;
	}
	public Value getValue() {
		return value;
	}
	public void setValue(Value value) {
		this.value = value;
	}
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
	@Override
	public int compareTo(CardCell o) {
		return row-o.getRow();
	}
}
