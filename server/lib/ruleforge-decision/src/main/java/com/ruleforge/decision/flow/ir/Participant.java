package com.ruleforge.decision.flow.ir;

import java.util.Objects;

/**
 * V5.37 B0 — BPMN 2.0 §12 {@code <bpmn:participant>} 不可变 IR。
 *
 * <p>1:1 指向一个 {@link FlowDefinition}(通过 {@link #getProcessRef()})。
 * v0 不带"resource assignment"等可选属性 — 仅 audit。
 */
public final class Participant {
    private final String id;
    private final String name;
    private final String processRef;

    public Participant(String id, String name, String processRef) {
        this.id = id;
        this.name = name;
        this.processRef = Objects.requireNonNull(processRef, "processRef is required");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getProcessRef() { return processRef; }
}
