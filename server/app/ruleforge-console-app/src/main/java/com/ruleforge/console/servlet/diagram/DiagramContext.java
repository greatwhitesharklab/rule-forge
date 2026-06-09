package com.ruleforge.console.servlet.diagram;

import com.ruleforge.model.Node;

import java.util.List;
import java.util.Map;

/**
 * @author Jacky.gao
 * @since 2015年1月6日
 */
public class DiagramContext {
    private int id;
    private List<Edge> edges;
    private Map<Node, NodeInfo> nodeMap;

    public DiagramContext(List<Edge> edges, Map<Node, NodeInfo> nodeMap) {
        this.edges = edges;
        this.nodeMap = nodeMap;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void addEdge(Edge edge) {
        edges.add(edge);
    }

    public Map<Node, NodeInfo> getNodeMap() {
        return nodeMap;
    }

    public void setNodeMap(Map<Node, NodeInfo> nodeMap) {
        this.nodeMap = nodeMap;
    }

    public int nextId() {
        id++;
        return id;
    }
}
