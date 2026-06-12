package com.ruleforge.decision.flow.ir;

import com.ruleforge.decision.flow.bus.MessageKind;

import java.util.Objects;

/**
 * V5.37 B0 — BPMN 2.0 §12 {@code <bpmn:messageFlow>} 不可变 IR。
 *
 * <p>4-tuple endpoint(sourceParticipantId / sourceNodeId / targetParticipantId / targetNodeId)
 * — 跨池 message flow 的连接边。{@link #channelName()} 派生 bus channel 名
 * (走 {@link MessageKind#channelFor(PoolMessage, String)}),其中 {@code name} 为
 * 显式 message flow 名称,缺省时 fallback 为 {@code <sourceNode>_to_<targetNode>}。
 */
public final class MessageFlow {
    private final String id;
    private final String name;
    private final String sourceParticipantId;
    private final String sourceNodeId;
    private final String targetParticipantId;
    private final String targetNodeId;

    public MessageFlow(String id, String name,
                       String sourceParticipantId, String sourceNodeId,
                       String targetParticipantId, String targetNodeId) {
        this.id = id;
        this.name = name;
        this.sourceParticipantId = Objects.requireNonNull(sourceParticipantId, "sourceParticipantId");
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        this.targetParticipantId = Objects.requireNonNull(targetParticipantId, "targetParticipantId");
        this.targetNodeId = Objects.requireNonNull(targetNodeId, "targetNodeId");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSourceParticipantId() { return sourceParticipantId; }
    public String getSourceNodeId() { return sourceNodeId; }
    public String getTargetParticipantId() { return targetParticipantId; }
    public String getTargetNodeId() { return targetNodeId; }

    public boolean matchesSource(String poolId, String nodeId) {
        return sourceParticipantId.equals(poolId) && sourceNodeId.equals(nodeId);
    }

    public boolean matchesTarget(String poolId, String nodeId) {
        return targetParticipantId.equals(poolId) && targetNodeId.equals(nodeId);
    }

    /**
     * 派生 bus channel:{@code "pool:<src>_to_<tgt>:<name>"}。
     * name 为 null 时 fallback {@code "<sourceNode>_to_<targetNode>"}。
     */
    public String channelName() {
        String nm = (name != null && !name.isBlank())
            ? name
            : (sourceNodeId + "_to_" + targetNodeId);
        return MessageKind.channelFor(
            new MessageKind.PoolMessage(sourceParticipantId, targetParticipantId), nm);
    }
}
