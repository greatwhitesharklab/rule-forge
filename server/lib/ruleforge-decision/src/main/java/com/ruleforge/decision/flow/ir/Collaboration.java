package com.ruleforge.decision.flow.ir;

import com.ruleforge.decision.exception.FlowExecutionException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V5.37 B0 — BPMN 2.0 §12 {@code <bpmn:collaboration>} 不可变 IR。
 *
 * <p>持有 N 个 {@link Participant} + M 条 {@link MessageFlow},构造期建索引。
 */
public final class Collaboration {
    private final String id;
    private final String name;
    private final List<Participant> participants;
    private final List<MessageFlow> messageFlows;
    private final Map<String, Participant> participantById;

    public Collaboration(String id, String name,
                         List<Participant> participants,
                         List<MessageFlow> messageFlows) {
        this.id = id;
        this.name = name;
        this.participants = participants == null ? List.of() : List.copyOf(participants);
        this.messageFlows = messageFlows == null ? List.of() : List.copyOf(messageFlows);

        // 索引 + 重复检测
        Map<String, Participant> byId = new HashMap<>();
        for (Participant p : this.participants) {
            if (byId.put(p.getId(), p) != null) {
                throw new FlowExecutionException(
                    "Duplicate participant id: " + p.getId() + " in collaboration " + id);
            }
        }
        this.participantById = Map.copyOf(byId);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<Participant> getParticipants() { return participants; }
    public List<MessageFlow> getMessageFlows() { return messageFlows; }

    public Optional<Participant> findParticipant(String participantId) {
        if (participantId == null) return Optional.empty();
        return Optional.ofNullable(participantById.get(participantId));
    }

    /** target 端查 — 找指向 {@code targetNodeId} 的所有 message flow。 */
    public List<MessageFlow> findIncoming(String targetNodeId) {
        if (targetNodeId == null) return List.of();
        List<MessageFlow> hits = new ArrayList<>();
        for (MessageFlow mf : messageFlows) {
            if (targetNodeId.equals(mf.getTargetNodeId())) hits.add(mf);
        }
        return hits;
    }

    /** source 端查 — 找从 {@code sourceNodeId} 出发的所有 message flow。 */
    public List<MessageFlow> findOutgoing(String sourceNodeId) {
        if (sourceNodeId == null) return List.of();
        List<MessageFlow> hits = new ArrayList<>();
        for (MessageFlow mf : messageFlows) {
            if (sourceNodeId.equals(mf.getSourceNodeId())) hits.add(mf);
        }
        return hits;
    }
}
