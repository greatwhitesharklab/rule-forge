package com.ruleforge.model.rete;
import com.ruleforge.engine.NodeActivityFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * RETE 节点基类(带 children + lines)。V5.76.6 移除:
 * <ul>
 *   <li>{@code newActivity} 抽象(已迁 {@code NodeActivityFactory})</li>
 *   <li>死代码 {@code buildVariables(Context, Value, Map)} / {@code fetchData(Object, String)}(全工程 0 caller)</li>
 *   <li>{@code import com.ruleforge.runtime.rete.Context} import(model→runtime 耦合消除)</li>
 * </ul>
 */
@Setter
@Getter
public abstract class BaseReteNode extends ReteNode {
    @JsonIgnore
    private List<ReteNode> childrenNodes = new ArrayList<ReteNode>();
    protected List<Line> lines;

    public BaseReteNode(int id) {
        super(id);
    }

    public Line addLine(ReteNode toNode) {
        if (childrenNodes == null) {
            childrenNodes = new ArrayList<>();
        }
        childrenNodes.add(toNode);
        Line line = new Line(this, toNode);
        if (lines == null) {
            lines = new ArrayList<>();
        }
        lines.add(line);
        if (toNode instanceof JunctionNode) {
            JunctionNode junctionNode = (JunctionNode) toNode;
            junctionNode.addToConnection(line);
        }
        return line;
    }
}
