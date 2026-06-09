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

    public FlowNode(String nodeId, NodeType type, String name,
                    Map<String, String> extensionAttrs,
                    String scriptText, String scriptFormat,
                    List<String> outgoingIds, boolean async) {
        this.nodeId = nodeId;
        this.type = type;
        this.name = name;
        this.extensionAttrs = extensionAttrs == null ? Map.of() : Map.copyOf(extensionAttrs);
        this.scriptText = scriptText;
        this.scriptFormat = scriptFormat == null ? "groovy" : scriptFormat;
        this.outgoingIds = outgoingIds == null ? List.of() : List.copyOf(outgoingIds);
        this.async = async;
    }

    public String getNodeId() { return nodeId; }
    public NodeType getType() { return type; }
    public String getName() { return name; }
    public Map<String, String> getExtensionAttrs() { return extensionAttrs; }
    public String getScriptText() { return scriptText; }
    public String getScriptFormat() { return scriptFormat; }
    public List<String> getOutgoingIds() { return outgoingIds; }
    public boolean isAsync() { return async; }

    public String attr(String key) { return extensionAttrs.get(key); }
    public String attr(String namespace, String name) {
        if (namespace == null || name == null) return null;
        return extensionAttrs.get(namespace + ":" + name);
    }
}
