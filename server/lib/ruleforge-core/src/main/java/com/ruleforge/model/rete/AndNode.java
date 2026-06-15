package com.ruleforge.model.rete;

/**
 * RETE AND 节点:所有入边都通过才触发下游。V5.76.6 后不再持有 {@code newActivity}(改由
 * {@code NodeActivityFactory} 创建 AndActivity)。
 */
public class AndNode extends JunctionNode {
    private NodeType nodeType = NodeType.and;

    public AndNode() {
        super(0);
    }

    public AndNode(int id) {
        super(id);
    }

    @Override
    public NodeType getNodeType() {
        return nodeType;
    }

    public void setToLineCount(int toLineCount) {
        this.toLineCount = toLineCount;
    }
}
