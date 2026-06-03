package com.ruleforge.console.servlet.decisiontable;

public class Header {
    private String name;
    private HeaderType type;

    public Header() {
        this.type = HeaderType.condition;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HeaderType getType() {
        return this.type;
    }

    public void setType(HeaderType type) {
        this.type = type;
    }
}
