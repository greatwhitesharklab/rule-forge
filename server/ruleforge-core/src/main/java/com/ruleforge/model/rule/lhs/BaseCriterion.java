package com.ruleforge.model.rule.lhs;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class BaseCriterion implements Criterion {
    @JsonIgnore
    private Junction parent;

    public Junction getParent() {
        return parent;
    }

    public void setParent(Junction parent) {
        this.parent = parent;
    }
}
