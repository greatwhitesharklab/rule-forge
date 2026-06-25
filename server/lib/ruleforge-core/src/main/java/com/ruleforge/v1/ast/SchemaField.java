package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Schema 字段定义。name = fact 字段名;type = 数据类型;label 中文标签(UI + CEL 自动补全)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SchemaField {
    private String name;
    private V1DataType type;
    private String label;
    private Boolean required;

    public SchemaField() {
    }

    public SchemaField(String name, V1DataType type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public V1DataType getType() {
        return type;
    }

    public void setType(V1DataType type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }
}
