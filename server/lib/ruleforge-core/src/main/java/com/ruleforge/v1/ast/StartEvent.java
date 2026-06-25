package com.ruleforge.v1.ast;

/**
 * BPMN startEvent。implementation = "Start:&lt;nodeId&gt;",引用 {@link RuleAsset#getNodes()} 里的 StartNode。
 */
public class StartEvent extends FlowElement {
    private String implementation;

    @Override
    public String getType() {
        return "startEvent";
    }

    public String getImplementation() {
        return implementation;
    }

    public void setImplementation(String implementation) {
        this.implementation = implementation;
    }
}
