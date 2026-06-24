package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DecisionTable 列定义。name 既是列名(AG Grid 表头)也是 fact 字段名;
 * field 可选,绑定的 fact 字段(默认 = name)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Column {
    private String name;
    private V1DataType dataType;
    private ColumnDirection direction;
    private String field;

    public Column() {
    }

    public Column(String name, V1DataType dataType, ColumnDirection direction) {
        this.name = name;
        this.dataType = dataType;
        this.direction = direction;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public V1DataType getDataType() {
        return dataType;
    }

    public void setDataType(V1DataType dataType) {
        this.dataType = dataType;
    }

    public ColumnDirection getDirection() {
        return direction;
    }

    public void setDirection(ColumnDirection direction) {
        this.direction = direction;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }
}
