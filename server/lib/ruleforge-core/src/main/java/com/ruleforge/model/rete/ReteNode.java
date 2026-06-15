package com.ruleforge.model.rete;

import com.ruleforge.model.Node;
import lombok.Getter;
import lombok.Setter;

/**
 * RETE 节点基类 —— 纯结构,不持有运行时行为(Activity 创建统一在
 * {@code com.ruleforge.runtime.rete.NodeActivityFactory})。
 *
 * <p>V5.76.6 TD-2.2 节点/Activity 分离:移除 {@code newActivity} 抽象,节点仅描述结构 + 提供
 * {@link #getNodeType()} 供 JSON 序列化/反序列化识别类型。
 */
@Setter
@Getter
public abstract class ReteNode implements Node {
    private int id;

    public ReteNode(int id) {
        this.id = id;
    }

    public abstract NodeType getNodeType();
}
