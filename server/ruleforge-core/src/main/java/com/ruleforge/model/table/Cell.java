package com.ruleforge.model.table;

import com.ruleforge.action.Action;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.rule.Value;

/**
 * @author Jacky.gao
 * @author fred
 * 2015年1月19日
 */
public class Cell {
    private int row;
    private int col;
    private int rowspan;
    private String variableLabel;
    private String variableName;
    private Datatype datatype;
    private Joint joint;
    private Value value;
    private Action action;

    public Cell() {
    }

    public int getRow() {
        return this.row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return this.col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public int getRowspan() {
        return this.rowspan;
    }

    public void setRowspan(int rowspan) {
        this.rowspan = rowspan;
    }

    public String getVariableLabel() {
        return this.variableLabel;
    }

    public void setVariableLabel(String variableLabel) {
        this.variableLabel = variableLabel;
    }

    public String getVariableName() {
        return this.variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public Datatype getDatatype() {
        return this.datatype;
    }

    public void setDatatype(Datatype datatype) {
        this.datatype = datatype;
    }

    public Joint getJoint() {
        return this.joint;
    }

    public void setJoint(Joint joint) {
        this.joint = joint;
    }

    public Action getAction() {
        return this.action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public Value getValue() {
        return this.value;
    }

    public void setValue(Value value) {
        this.value = value;
    }
}
