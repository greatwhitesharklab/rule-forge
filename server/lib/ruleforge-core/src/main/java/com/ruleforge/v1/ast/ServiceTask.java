package com.ruleforge.v1.ast;

/**
 * BPMN serviceTask。implementation = "&lt;NodeType&gt;:&lt;nodeId&gt;",如 "RuleSet:precheck",
 * 引用 {@link RuleAsset#getNodes()} 里对应业务节点。引擎执行时反查并调对应节点执行器。
 */
public class ServiceTask extends FlowElement {
    private String implementation;

    @Override
    public String getType() {
        return "serviceTask";
    }

    public String getImplementation() {
        return implementation;
    }

    public void setImplementation(String implementation) {
        this.implementation = implementation;
    }
}
