package com.ruleforge.model.rete;

import com.ruleforge.model.rule.Rule;

/**
 * RETE 终端节点:挂一条 {@link Rule}。V5.76.6 后不再持有 {@code newActivity}(改由
 * {@code NodeActivityFactory} 创建 TerminalActivity)。
 */
public class TerminalNode extends ReteNode {
    private Rule rule;
    private NodeType nodeType = NodeType.terminal;

    public TerminalNode() {
        super(0);
    }

    public TerminalNode(Rule rule, int id) {
        super(id);
        this.rule = rule;
    }

    @Override
    public NodeType getNodeType() {
        return nodeType;
    }

    public Rule[] enter(com.ruleforge.runtime.rete.Context context, Object object) {
        return new Rule[]{rule};
    }

    public Rule getRule() {
        return rule;
    }

    public void setRule(Rule rule) {
        this.rule = rule;
    }
}
