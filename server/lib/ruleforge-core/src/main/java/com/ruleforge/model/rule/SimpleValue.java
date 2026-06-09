package com.ruleforge.model.rule;

/**
 * @author Jacky.gao
 * @date 2014年12月29日
 */
public class SimpleValue extends AbstractValue {
    private String content;
    private ValueType valueType = ValueType.Input;

    public ValueType getValueType() {
        return valueType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getId() {
        String id = "[字符]" + content;
        if (arithmetic != null) {
            id += arithmetic.getId();
        }
        return id;
    }
}
