package com.ruleforge.model.rete;

import com.ruleforge.model.Node;
import com.ruleforge.runtime.rete.Activity;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Setter
@Getter
public abstract class ReteNode implements Node {
    private int id;

    public ReteNode(int id) {
        this.id = id;
    }

    public abstract NodeType getNodeType();

    public abstract Activity newActivity(Map<Object, Object> context);

}
