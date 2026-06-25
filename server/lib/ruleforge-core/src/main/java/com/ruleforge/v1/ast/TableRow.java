package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * DecisionTable 行。conditions 每个输入列一个 CEL 表达式,'*' = 通配(任意命中);
 * outputs 每个输出列一个字面量。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TableRow {
    private String id;
    private List<String> conditions;
    private List<Object> outputs;
    /** 行标签(运营备注)。 */
    private String annotation;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<String> getConditions() {
        return conditions;
    }

    public void setConditions(List<String> conditions) {
        this.conditions = conditions;
    }

    public List<Object> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<Object> outputs) {
        this.outputs = outputs;
    }

    public String getAnnotation() {
        return annotation;
    }

    public void setAnnotation(String annotation) {
        this.annotation = annotation;
    }
}
