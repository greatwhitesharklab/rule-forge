package com.ruleforge.model.rete;

/**
 * RETE OR 节点:任一入边通过即触发下游。V5.76.6 后不再持有 {@code newActivity}(改由
 * {@code NodeActivityFactory} 创建 OrActivity)。
 */
public class OrNode extends JunctionNode {
    private NodeType nodeType = NodeType.or;

    public OrNode() {
        super(0);
    }

    public OrNode(int id) {
        super(id);
    }

    @Override
    public NodeType getNodeType() {
        return nodeType;
    }
}
