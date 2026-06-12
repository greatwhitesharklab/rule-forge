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
    /**
     * V5.34 A3 — activityId → handlerNodeIds(从
     * {@code <bpmn:compensateIntermediateThrowEvent ruleforge:attachedToRef="..."/>}
     * 解析而来,顺序 = 解析顺序 = 倒序遍历的"末班" = LIFO 跑)。
     */
    private final Map<String, List<String>> attachedCompensations;
    /**
     * V5.35 A5 — linkName → catchNodeId(从
     * {@code <bpmn:intermediateCatchEvent ruleforge:eventType="linkCatch" ruleforge:linkName="..."/>}
     * 解析而来,linkThrow 节点通过 def.linkTargets[name] 跳到 catch)。
     */
    private final Map<String, String> linkTargets;
    /**
     * V5.37 B0 — 所属 collaboration id(nullable — 单 process 时为 null)。
     * 关联回 {@link com.ruleforge.decision.flow.ir.BpmnDefinition#collaboration()} 的 id。
     */
    private final String collaborationId;
    /**
     * V5.37 B0 — lane id → {@link Lane}。v0 简化:lane 仅 audit 记录,executor 不 gate;
     * cross-process lane sharing 不支持。
     */
    private final Map<String, Lane> lanes;

    public FlowDefinition(String processId, String name,
                          Map<String, FlowNode> nodes, List<SequenceFlow> edges,
                          String startNodeId, List<String> endNodeIds,
                          String sourceXml, String sourceXmlHash, Instant parsedAt) {
        this(processId, name, nodes, edges, startNodeId, endNodeIds,
            sourceXml, sourceXmlHash, parsedAt, java.util.Map.of(), java.util.Map.of(),
            null, java.util.Map.of());
    }

    public FlowDefinition(String processId, String name,
                          Map<String, FlowNode> nodes, List<SequenceFlow> edges,
                          String startNodeId, List<String> endNodeIds,
                          String sourceXml, String sourceXmlHash, Instant parsedAt,
                          Map<String, List<String>> attachedCompensations) {
        this(processId, name, nodes, edges, startNodeId, endNodeIds,
            sourceXml, sourceXmlHash, parsedAt, attachedCompensations, java.util.Map.of(),
            null, java.util.Map.of());
    }

    public FlowDefinition(String processId, String name,
                          Map<String, FlowNode> nodes, List<SequenceFlow> edges,
                          String startNodeId, List<String> endNodeIds,
                          String sourceXml, String sourceXmlHash, Instant parsedAt,
                          Map<String, List<String>> attachedCompensations,
                          Map<String, String> linkTargets) {
        this(processId, name, nodes, edges, startNodeId, endNodeIds,
            sourceXml, sourceXmlHash, parsedAt, attachedCompensations, linkTargets,
            null, java.util.Map.of());
    }

    /** V5.37 B0 — 13-field ctor(collaborationId + lanes)。 */
    public FlowDefinition(String processId, String name,
                          Map<String, FlowNode> nodes, List<SequenceFlow> edges,
                          String startNodeId, List<String> endNodeIds,
                          String sourceXml, String sourceXmlHash, Instant parsedAt,
                          Map<String, List<String>> attachedCompensations,
                          Map<String, String> linkTargets,
                          String collaborationId,
                          Map<String, Lane> lanes) {
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
        this.attachedCompensations = attachedCompensations == null
            ? java.util.Map.of()
            : attachedCompensations;
        this.linkTargets = linkTargets == null
            ? java.util.Map.of()
            : Map.copyOf(linkTargets);
        this.collaborationId = collaborationId;
        this.lanes = lanes == null ? java.util.Map.of() : Map.copyOf(lanes);
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
    public Map<String, List<String>> getAttachedCompensations() { return attachedCompensations; }
    public Map<String, String> getLinkTargets() { return linkTargets; }
    public String getCollaborationId() { return collaborationId; }
    public Map<String, Lane> getLanes() { return lanes; }

    public FlowNode getNode(String nodeId) { return nodes.get(nodeId); }
    public SequenceFlow getEdge(String edgeId) { return edgesById.get(edgeId); }
}
