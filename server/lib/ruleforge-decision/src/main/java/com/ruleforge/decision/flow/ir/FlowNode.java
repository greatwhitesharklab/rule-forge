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
    /**
     * V5.38 C1 — 单 pool 内 send/receive 消息引用(nullable — 非 SEND/RECEIVE_TASK 节点时为 null)。
     * 跟 {@link #messageFlowId} 字段正交:
     * <ul>
     *   <li>messageFlowId — B0 跨池 message flow 端点(START/END_EVENT 节点带)</li>
     *   <li>messageRef — C1 单 pool send/receive 消息引用(SEND_TASK/RECEIVE_TASK 节点带)</li>
     * </ul>
     */
    private final String messageRef;

    public FlowNode(String nodeId, NodeType type, String name,
                    Map<String, String> extensionAttrs,
                    String scriptText, String scriptFormat,
                    List<String> outgoingIds, boolean async) {
        this(nodeId, type, name, extensionAttrs, scriptText, scriptFormat, outgoingIds, async,
            null, null, null);
    }

    /** V5.37 B0 — 10-field ctor(laneId + messageFlowId),向后兼容 C1 之前 caller。 */
    public FlowNode(String nodeId, NodeType type, String name,
                    Map<String, String> extensionAttrs,
                    String scriptText, String scriptFormat,
                    List<String> outgoingIds, boolean async,
                    String laneId, String messageFlowId) {
        this(nodeId, type, name, extensionAttrs, scriptText, scriptFormat, outgoingIds, async,
            laneId, messageFlowId, null);
    }

    /** V5.38 C1 — 11-field ctor(+ messageRef)。 */
    public FlowNode(String nodeId, NodeType type, String name,
                    Map<String, String> extensionAttrs,
                    String scriptText, String scriptFormat,
                    List<String> outgoingIds, boolean async,
                    String laneId, String messageFlowId, String messageRef) {
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
        this.messageRef = messageRef;
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
    public String getMessageRef() { return messageRef; }

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
            laneId, messageFlowId, messageRef);
    }
}
