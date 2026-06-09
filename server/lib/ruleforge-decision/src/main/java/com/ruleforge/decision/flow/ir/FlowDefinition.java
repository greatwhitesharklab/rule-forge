package com.ruleforge.decision.flow.ir;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 不可变的决策流定义(BPMN process 解析后的 IR 根)。
 *
 * 关键不变量:
 * - nodes: nodeId → FlowNode,顺序无关
 * - edges: 所有 sequenceFlow 的扁平列表,BpmnNodeRunner 按 sourceId 分组查找 outgoing
 * - sourceXmlHash: 状态恢复时校验 IR 是否失效
 */
public final class FlowDefinition {
    private final String processId;
    private final String name;
    private final Map<String, FlowNode> nodes;
    private final List<SequenceFlow> edges;
    private final Map<String, SequenceFlow> edgesById;
    private final String startNodeId;
    private final List<String> endNodeIds;
    private final String sourceXml;
    private final String sourceXmlHash;
    private final Instant parsedAt;

    public FlowDefinition(String processId, String name,
                          Map<String, FlowNode> nodes, List<SequenceFlow> edges,
                          String startNodeId, List<String> endNodeIds,
                          String sourceXml, String sourceXmlHash, Instant parsedAt) {
        this.processId = processId;
        this.name = name;
        this.nodes = Map.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.edgesById = edges.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            SequenceFlow::getId, java.util.function.Function.identity()));
        this.startNodeId = startNodeId;
        this.endNodeIds = List.copyOf(endNodeIds);
        this.sourceXml = sourceXml;
        this.sourceXmlHash = sourceXmlHash;
        this.parsedAt = parsedAt;
    }

    public String getProcessId() { return processId; }
    public String getName() { return name; }
    public Map<String, FlowNode> getNodes() { return nodes; }
    public List<SequenceFlow> getEdges() { return edges; }
    public Map<String, SequenceFlow> getEdgesById() { return edgesById; }
    public String getStartNodeId() { return startNodeId; }
    public List<String> getEndNodeIds() { return endNodeIds; }
    public String getSourceXml() { return sourceXml; }
    public String getSourceXmlHash() { return sourceXmlHash; }
    public Instant getParsedAt() { return parsedAt; }

    public FlowNode getNode(String nodeId) { return nodes.get(nodeId); }
    public SequenceFlow getEdge(String edgeId) { return edgesById.get(edgeId); }
}
