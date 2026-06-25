package com.ruleforge.v1.ast;

import java.util.Map;

/**
 * 流程入口 + 输入定义。schema 引用 {@link Schema} 名(定义输入 fact 字段)。
 */
public class StartNode extends NodeBase {
    private String schema;
    /** 可选 fact 字段别名,如 {@code {"income": "monthlyIncome"}}。 */
    private Map<String, String> inputMapping;

    @Override
    public String getType() {
        return "Start";
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public Map<String, String> getInputMapping() {
        return inputMapping;
    }

    public void setInputMapping(Map<String, String> inputMapping) {
        this.inputMapping = inputMapping;
    }
}
