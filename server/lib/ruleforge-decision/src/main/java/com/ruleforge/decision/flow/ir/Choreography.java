package com.ruleforge.decision.flow.ir;

import com.ruleforge.decision.exception.FlowExecutionException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * V5.37 B1 — BPMN 2.0 §11 Choreography 顶层 IR(对话协议层)。
 *
 * <p>跟 §12 {@link Collaboration} 平级,关注点不同:
 * <ul>
 *   <li>Collaboration:谁参与 + transport 怎么走</li>
 *   <li>Choreography:他们怎么对话(谁先说、谁接)</li>
 * </ul>
 *
 * <p>运行时 Choreography 不进 executor — 纯 IR + parser 校验。executor 侧
 * 仍走 B0 {@link com.ruleforge.decision.flow.executor.MessageFlowStartExecutor}
 * / {@link com.ruleforge.decision.flow.executor.MessageFlowEndExecutor}。
 */
public final class Choreography {

    private final String id;
    private final String name;
    private final List<ChoreographyTask> tasks;            // 顺序保留
    private final Map<String, ChoreographyTask> taskById;  // 索引

    public Choreography(String id, String name, List<ChoreographyTask> tasks) {
        this.id = id;
        this.name = name;
        this.tasks = tasks == null ? List.of() : List.copyOf(tasks);
        Map<String, ChoreographyTask> idx = new LinkedHashMap<>();
        for (ChoreographyTask t : this.tasks) {
            if (idx.put(t.getId(), t) != null) {
                throw new FlowExecutionException(
                    "Choreography " + id + ": duplicate task id " + t.getId());
            }
        }
        this.taskById = Map.copyOf(idx);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<ChoreographyTask> getTasks() { return tasks; }

    public Optional<ChoreographyTask> findTask(String taskId) {
        if (taskId == null) return Optional.empty();
        return Optional.ofNullable(taskById.get(taskId));
    }

    /** V5.37 B1 — 跟 collaboration 交叉校验:每个 task 的 messageFlowId 必须在 collab 里存在。 */
    public void validateMessageFlowRefs(Collaboration collab) {
        if (collab == null) return;  // 单 process 时不做校验
        for (ChoreographyTask t : tasks) {
            if (t.getMessageFlowId() == null) continue;
            boolean found = collab.getMessageFlows().stream()
                .anyMatch(mf -> t.getMessageFlowId().equals(mf.getId()));
            if (!found) {
                throw new FlowExecutionException(
                    "Choreography " + id + " task " + t.getId()
                    + " references unknown messageFlowId=" + t.getMessageFlowId()
                    + " (not in collaboration " + collab.getId() + ")");
            }
        }
    }
}
