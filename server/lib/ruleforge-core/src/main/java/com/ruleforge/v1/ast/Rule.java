package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * RuleSet 内单条规则。condition(CEL,返回 boolean)+ actions(结构化)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Rule {
    private String id;
    private String name;
    /** PRIORITY 策略下数字大先评估;默认 0。 */
    private Integer priority;
    /** 默认 true,可临时禁用。 */
    private Boolean enabled;
    /** CEL 表达式,返回 boolean。 */
    private String condition;
    private List<Action> actions;

    public Rule() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }
}
