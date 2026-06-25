package com.ruleforge.v1.ast.library;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ruleforge.v1.ast.V1DataType;

/**
 * 库条目(vl 字段 / cl 常量 / pl 参数)。{@code key} = 引用名(CEL 里 param.{key}),
 * {@code value} = 字面量值(pl/cl),{@code dataType} = 类型,{@code label} = 中文标签。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LibraryEntry {
    private String key;
    private Object value;
    private V1DataType dataType;
    private String label;

    public LibraryEntry() {
    }

    public LibraryEntry(String key, Object value, V1DataType dataType, String label) {
        this.key = key;
        this.value = value;
        this.dataType = dataType;
        this.label = label;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public V1DataType getDataType() {
        return dataType;
    }

    public void setDataType(V1DataType dataType) {
        this.dataType = dataType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
