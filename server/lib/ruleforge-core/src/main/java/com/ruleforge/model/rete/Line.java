package com.ruleforge.model.rete;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import com.ruleforge.model.Node;
import com.ruleforge.runtime.rete.Path;

/**
 * @author Jacky.gao
 * 2015年1月6日
 */
@Setter
public class Line {
    @Getter
    private int fromNodeId;
    @Getter
    private int toNodeId;
    @JsonIgnore
    private ReteNode from;
    @JsonIgnore
    private ReteNode to;

    public Line() {
    }

    public Line(ReteNode from, ReteNode to) {
        this.from = from;
        this.to = to;
        this.fromNodeId = from.getId();
        this.toNodeId = to.getId();
    }

    public Node getFrom() {
        return from;
    }

    public Node getTo() {
        return to;
    }

    public Path newPath(Map<Object, Object> context) {
        return new Path(to.newActivity(context));
    }

}
