package com.ruleforge.decision.flow.ir;

import com.ruleforge.decision.exception.FlowExecutionException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * V5.37 B0 — BPMN 2.0 顶层 IR(record)。
 *
 * <p>两个组成:
 * <ul>
 *   <li>{@code collaboration}: 可空 — 单 process 时 null;多池时含 N 个 participant + M 条 message flow</li>
 *   <li>{@code processes}: processId → {@link FlowDefinition},多池时含 1+ process</li>
 * </ul>
 *
 * <p>向后兼容:单 process caller 用 {@link #ofSingleProcess(FlowDefinition)} 包装。
 */
public record BpmnDefinition(
    Collaboration collaboration,
    Map<String, FlowDefinition> processes
) {

    /** 紧凑 ctor — 防御性复制 processes map + values。 */
    public BpmnDefinition {
        processes = processes == null
            ? Map.of()
            : Map.copyOf(processes);
    }

    /** 单 process 向后兼容 wrapper(collaboration = null)。 */
    public static BpmnDefinition ofSingleProcess(FlowDefinition def) {
        if (def == null) throw new FlowExecutionException("FlowDefinition is null");
        return new BpmnDefinition(null, Map.of(def.getProcessId(), def));
    }

    /** 取 process(找不到抛错)。 */
    public FlowDefinition requireProcess(String processId) {
        FlowDefinition def = processes.get(processId);
        if (def == null) {
            throw new FlowExecutionException("Process not found: " + processId
                + (collaboration != null ? " in collaboration " + collaboration.getId() : ""));
        }
        return def;
    }

    /** 按 participantId 查 participant(单 process 时返空)。 */
    public Optional<Participant> findParticipant(String participantId) {
        if (collaboration == null) return Optional.empty();
        return collaboration.findParticipant(participantId);
    }

    /** 按 messageFlowId 查 message flow(单 process 时返空)。 */
    public Optional<MessageFlow> findMessageFlow(String messageFlowId) {
        if (collaboration == null || messageFlowId == null) return Optional.empty();
        for (MessageFlow mf : collaboration.getMessageFlows()) {
            if (messageFlowId.equals(mf.getId())) return Optional.of(mf);
        }
        return Optional.empty();
    }

    /** 按 (poolId, nodeId) 找 source 端 message flow(用于 publish 端). */
    public Optional<MessageFlow> findSourceMessageFlowAt(String poolId, String nodeId) {
        if (collaboration == null || poolId == null || nodeId == null) return Optional.empty();
        for (MessageFlow mf : collaboration.getMessageFlows()) {
            if (mf.matchesSource(poolId, nodeId)) return Optional.of(mf);
        }
        return Optional.empty();
    }

    /** 按 (poolId, nodeId) 找 target 端 message flow(用于 subscribe 端). */
    public Optional<MessageFlow> findTargetMessageFlowAt(String poolId, String nodeId) {
        if (collaboration == null || poolId == null || nodeId == null) return Optional.empty();
        for (MessageFlow mf : collaboration.getMessageFlows()) {
            if (mf.matchesTarget(poolId, nodeId)) return Optional.of(mf);
        }
        return Optional.empty();
    }

    /** 是否多池(collaboration != null)。 */
    public boolean isMultiPool() {
        return collaboration != null;
    }

    /** 按插入顺序取 processId 列表。 */
    public java.util.List<String> getProcessIds() {
        if (processes instanceof LinkedHashMap lm) return new java.util.ArrayList<>(lm.keySet());
        return new java.util.ArrayList<>(processes.keySet());
    }
}
