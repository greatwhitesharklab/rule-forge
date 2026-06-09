package com.ruleforge.model.table;

import com.ruleforge.model.library.Datatype;

/**
 * @author Jacky.gao
 * 2015年1月19日
 */
public class Column implements Comparable<Column> {
    private int num;
    private int width;
    private String variableCategory;
    private String variableLabel;
    private String variableName;
    private Datatype datatype;
    private ColumnType type;

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public String getVariableCategory() {
        return variableCategory;
    }

    public void setVariableCategory(String variableCategory) {
        this.variableCategory = variableCategory;
    }

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

    public ColumnType getType() {
        return type;
    }

    public void setType(ColumnType type) {
        this.type = type;
    }

    public Datatype getDatatype() {
        return datatype;
    }

    public void setDatatype(Datatype datatype) {
        this.datatype = datatype;
    }

    public int compareTo(Column o) {
        return o.getNum() - num;
    }
}
