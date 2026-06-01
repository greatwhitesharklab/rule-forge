package com.ruleforge.console.servlet.diagram;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author Jacky.gao
 * 2015年1月6日
 */
@Setter
@Getter
public class Diagram {
    private List<Edge> edges;
    private NodeInfo rootNode;
    private int width;
    private int height;

    public Diagram(List<Edge> edges, NodeInfo rootNode) {
        this.edges = edges;
        this.rootNode = rootNode;
    }
}
