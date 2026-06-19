package com.ruleforge.model.rete;
import com.ruleforge.engine.NodeActivityFactory;

/**
 * RETE 对象类型节点:按类名/类匹配 fact。V5.76.6 后不再持有 {@code newActivity}(改由
 * {@code NodeActivityFactory} 创建 ObjectTypeActivity)。
 */
public class ObjectTypeNode extends BaseReteNode {
    public static final String NON_CLASS = "*";
    private String objectTypeClass;
    private NodeType nodeType = NodeType.objectType;

    public ObjectTypeNode() {
        super(0);
    }

    public ObjectTypeNode(String objectTypeClass, int id) {
        super(id);
        this.objectTypeClass = objectTypeClass;
    }

    @Override
    public NodeType getNodeType() {
        return nodeType;
    }

    public boolean support(Object object) {
        return support(object.getClass().getName());
    }

    public boolean support(String className) {
        return objectTypeClass.equals(className);
    }

    public String getObjectTypeClass() {
        return objectTypeClass;
    }

    public void setObjectTypeClass(String objectTypeClass) {
        this.objectTypeClass = objectTypeClass;
    }
}
