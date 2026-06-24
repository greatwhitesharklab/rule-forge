package com.ruleforge.v1.ast;

/**
 * BPMN endEvent。implementation = "Decision:&lt;nodeId&gt;",引用 {@link DecisionNode}(流程终点)。
 */
public class EndEvent extends FlowElement {
    private String implementation;

    @Override
    public String getType() {
        return "endEvent";
    }

    public String getImplementation() {
        return implementation;
    }

    public void setImplementation(String implementation) {
        this.implementation = implementation;
    }
}
