package com.ruleforge.v1.ast;

import java.util.List;

/**
 * 输入 fact 结构定义(替代 urule vl 库,V1 精简)。StartNode.schema 引用其 {@link #name}。
 * cl/pl/al 4 库留 V1.1+;V1 只做 Schema。
 */
public class Schema {
    private String name;
    private List<SchemaField> fields;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<SchemaField> getFields() {
        return fields;
    }

    public void setFields(List<SchemaField> fields) {
        this.fields = fields;
    }
}
