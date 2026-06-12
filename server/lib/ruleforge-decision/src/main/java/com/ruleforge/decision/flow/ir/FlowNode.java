package com.ruleforge.decision.flow.ir;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 不可变的 IR 节点。所有 BPMN 节点类型共用。
 * 通过 extensionAttrs 携带 ruleforge: / flowable: 扩展属性。
 */
public final class FlowNode {
    private final String nodeId;
    private final NodeType type;
    private final String name;
    private final Map<String, String> extensionAttrs;
    private final String scriptText;
    private final String scriptFormat;
    private final List<String> outgoingIds;
    private final boolean async;
    /**
     * V5.37 B0 — 所属 lane id(nullable — 单 process / 不在 laneSet 时为 null)。
     * v0 简化:lane 仅 audit 记录,executor 不 gate。
     */
    private final String laneId;
    /**
     * V5.37 B0 — 关联 message flow id(nullable — 非 message flow 端点时为 null)。
     * 仅 START_EVENT / END_EVENT 节点用;值 = {@link com.ruleforge.decision.flow.ir.MessageFlow#getId()}。
     */
    private final String messageFlowId;

    public FlowNode(String nodeId, NodeType type, String name,
                    Map<String, String> extensionAttrs,
                    String scriptText, String scriptFormat,
                    List<String> outgoingIds, boolean async) {
        this(nodeId, type, name, extensionAttrs, scriptText, scriptFormat, outgoingIds, async,
            null, null);
    }

    /** V5.37 B0 — 10-field ctor(laneId + messageFlowId)。 */
    public FlowNode(String nodeId, NodeType type, String name,
                    Map<String, String> extensionAttrs,
                    String scriptText, String scriptFormat,
                    List<String> outgoingIds, boolean async,
                    String laneId, String messageFlowId) {
        this.nodeId = nodeId;
        this.type = type;
        this.name = name;
        this.extensionAttrs = extensionAttrs == null ? Map.of() : Map.copyOf(extensionAttrs);
        this.scriptText = scriptText;
        this.scriptFormat = scriptFormat == null ? "groovy" : scriptFormat;
        this.outgoingIds = outgoingIds == null ? List.of() : List.copyOf(outgoingIds);
        this.async = async;
        this.laneId = laneId;
        this.messageFlowId = messageFlowId;
    }

    public String getNodeId() { return nodeId; }
    public NodeType getType() { return type; }
    public String getName() { return name; }
    public Map<String, String> getExtensionAttrs() { return extensionAttrs; }
    public String getScriptText() { return scriptText; }
    public String getScriptFormat() { return scriptFormat; }
    public List<String> getOutgoingIds() { return outgoingIds; }
    public boolean isAsync() { return async; }
    public String getLaneId() { return laneId; }
    public String getMessageFlowId() { return messageFlowId; }

    public String attr(String key) { return extensionAttrs.get(key); }
    public String attr(String namespace, String name) {
        if (namespace == null || name == null) return null;
        return extensionAttrs.get(namespace + ":" + name);
    }

    /**
     * V5.33 A1 — 返回一个新 FlowNode,从 extensionAttrs 移除指定 key。
     * 用途:MultiInstanceExecutor 递归 resolve inner 时,临时剥掉 multiInstance attr
     * 避免 registry 路由回 wrapper。
     */
    public FlowNode withoutAttr(String key) {
        if (key == null || !extensionAttrs.containsKey(key)) return this;
        Map<String, String> newExt = new java.util.HashMap<>(extensionAttrs);
        newExt.remove(key);
        return new FlowNode(nodeId, type, name, newExt, scriptText, scriptFormat, outgoingIds, async,
            laneId, messageFlowId);
    }
}
