package com.ruleforge.decision.flow.ir;

import java.util.List;

/**
 * V5.37 B0 — BPMN 2.0 §12 {@code <bpmn:lane>} 不可变 IR。
 *
 * <p>v0 简化:
 * <ul>
 *   <li>lane 是 organizational only — executor 不 gate</li>
 *   <li>Lane 不做 node 引用合法性校验(留到 parser 阶段统一报)</li>
 *   <li>Lane 不强制 unique id(留到 Collaboration / parser 阶段报)</li>
 *   <li>getNodeIds() 不递归(嵌套靠 parentLaneId 链,parent 在 IR 装配阶段聚合)</li>
 * </ul>
 */
public final class Lane {
    private final String id;
    private final String name;
    private final String parentLaneId;
    private final List<String> flowNodeRefs;

    public Lane(String id, String name, String parentLaneId, List<String> flowNodeRefs) {
        this.id = id;
        this.name = name;
        this.parentLaneId = parentLaneId;
        this.flowNodeRefs = flowNodeRefs == null ? List.of() : List.copyOf(flowNodeRefs);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getParentLaneId() { return parentLaneId; }
    public List<String> getFlowNodeRefs() { return flowNodeRefs; }

    /** 直属 node ids(不递归)。v0 简化:parent 在 IR 装配时已包含所有 nodes。 */
    public List<String> getNodeIds() { return flowNodeRefs; }
}
