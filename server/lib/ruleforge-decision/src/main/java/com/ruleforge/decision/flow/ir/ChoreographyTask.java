package com.ruleforge.decision.flow.ir;

import java.util.List;
import java.util.Objects;

/**
 * V5.37 B1 — BPMN 2.0 §11 Choreography Task(原子对话)。
 *
 * <p>一个 choreography task 代表两个 pool 之间的一次"对话":
 * <ul>
 *   <li>{@link #initiatingParticipantId} — 谁先开口(对应 BPMN initiatingParticipantRef)</li>
 *   <li>{@link #firstParticipantId} — 参与方 1(对应 firstParticipantRef)</li>
 *   <li>{@link #secondParticipantId} — 参与方 2(对应 secondParticipantRef)</li>
 *   <li>{@link #messageFlowId} — 反向引用 §12 message flow(B0 的 transport)</li>
 *   <li>{@link #outgoingTaskIds} — 编排顺序(可空,v0 仅 audit)</li>
 * </ul>
 *
 * <p>v0 简化:
 * <ul>
 *   <li>❌ participantBand 显式标注(initiating/first/second 已足够区分)</li>
 *   <li>❌ correlationKey(走 messageFlow 的 payload)</li>
 *   <li>❌ 嵌套 sub-choreography(B2 范畴)</li>
 *   <li>❌ choreography 内部数据关联(走 message flow 即可)</li>
 * </ul>
 */
public final class ChoreographyTask {

    private final String id;
    private final String name;
    private final String initiatingParticipantId;
    private final String firstParticipantId;
    private final String secondParticipantId;
    private final String messageFlowId;
    private final List<String> outgoingTaskIds;

    public ChoreographyTask(String id,
                            String name,
                            String initiatingParticipantId,
                            String firstParticipantId,
                            String secondParticipantId,
                            String messageFlowId,
                            List<String> outgoingTaskIds) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = name;
        // 三个角色必填 — BPMN spec 强制
        this.initiatingParticipantId = Objects.requireNonNull(initiatingParticipantId,
            "initiatingParticipantId");
        this.firstParticipantId = Objects.requireNonNull(firstParticipantId, "firstParticipantId");
        this.secondParticipantId = Objects.requireNonNull(secondParticipantId, "secondParticipantId");
        // messageFlowId 可空 — v0 choreography task 可独立存在(无 transport 绑定)
        this.messageFlowId = messageFlowId;
        this.outgoingTaskIds = outgoingTaskIds == null ? List.of() : List.copyOf(outgoingTaskIds);

        // 业务不变量:initiating 必须是 first 或 second
        if (!initiatingParticipantId.equals(firstParticipantId)
            && !initiatingParticipantId.equals(secondParticipantId)) {
            throw new IllegalArgumentException(
                "ChoreographyTask " + id + ": initiatingParticipantRef=" + initiatingParticipantId
                + " must be one of firstParticipantRef=" + firstParticipantId
                + " or secondParticipantRef=" + secondParticipantId);
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getInitiatingParticipantId() { return initiatingParticipantId; }
    public String getFirstParticipantId() { return firstParticipantId; }
    public String getSecondParticipantId() { return secondParticipantId; }
    public String getMessageFlowId() { return messageFlowId; }
    public List<String> getOutgoingTaskIds() { return outgoingTaskIds; }
}
