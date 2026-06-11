package com.ruleforge.decision.flow;

import com.ruleforge.decision.exception.FlowExecutionException;

import java.util.Map;

/**
 * V5.34 A2 — EndEvent 节点种类(SEALED 模拟 Rust enum)。
 *
 * <p>Mirror Rust V5.30 {@code end_event.rs} 4 variant 契约:
 * <ul>
 *   <li>{@link None} — 正常 end,traverse COMPLETED</li>
 *   <li>{@link Error} — errorRef → ctx.thrownError + FlowExecutionException</li>
 *   <li>{@link Escalation} — escalationRef → ctx.thrownError + FlowExecutionException</li>
 *   <li>{@link Terminate} — V5.30 v0 跟 Error 同 path,V5.31 P1 才加 token-kill</li>
 * </ul>
 *
 * <p>{@code fromAttrs} 工厂规则:
 * <ul>
 *   <li>无 {@code ruleforge:endType} → None</li>
 *   <li>{@code endType=error} → Error(无 {@code errorRef} 报错,空串也报错)</li>
 *   <li>{@code endType=escalation} → Escalation(无 {@code escalationRef} 报错)</li>
 *   <li>{@code endType=terminate} → Terminate</li>
 *   <li>其他值 → 报错</li>
 * </ul>
 *
 * <p>A6 会扩 4 variant(cancel / compensation / messageEnd / signalEnd)。
 */
public sealed interface EndEventKind permits EndEventKind.None,
                                            EndEventKind.Error,
                                            EndEventKind.Escalation,
                                            EndEventKind.Terminate {

    /** 正常 end,无特殊语义。 */
    final class None implements EndEventKind {
        public static final None INSTANCE = new None();
        private None() {}
        /** 为对称性,None 永远返回 null;避免 pattern match 强转。 */
        public String errorRef() { return null; }
    }

    /** endType=error 严格化 end。 */
    final class Error implements EndEventKind {
        private final String errorRef;
        public Error(String errorRef) { this.errorRef = errorRef; }
        public String errorRef() { return errorRef; }
    }

    /** endType=escalation 严格化 end。 */
    final class Escalation implements EndEventKind {
        private final String escalationRef;
        public Escalation(String escalationRef) { this.escalationRef = escalationRef; }
        public String errorRef() { return escalationRef; }
        public String escalationRef() { return escalationRef; }
    }

    /** endType=terminate — V5.30 v0 跟 Error 同 path,token-kill 留 V5.31 P1。 */
    final class Terminate implements EndEventKind {
        public static final Terminate INSTANCE = new Terminate();
        private Terminate() {}
        public String errorRef() { return null; }
    }

    /** 通用 errorRef getter(NONE/ERROR/ESCALATION/TERMINATE 都有)。 */
    String errorRef();

    // -------- factory --------

    /**
     * 从 BPMN 节点的 extensionAttrs 解析 EndEventKind。
     *
     * @param attrs {@code key=value} map,key 形式如 {@code ruleforge:endType} / {@code ruleforge:errorRef}
     * @return 4 variant 之一
     * @throws FlowExecutionException 缺必填字段或不识别 endType
     */
    static EndEventKind fromAttrs(Map<String, String> attrs) {
        if (attrs == null) return None.INSTANCE;
        String endType = attrs.get("ruleforge:endType");
        if (endType == null || endType.isBlank()) {
            return None.INSTANCE;
        }
        return switch (endType) {
            case "error" -> {
                String ref = attrs.get("ruleforge:errorRef");
                if (ref != null && ref.isBlank()) {
                    throw new FlowExecutionException(
                        "EndEvent endType=error has blank ruleforge:errorRef (omit it for default \"error\")");
                }
                // V5.30:无 ref 时默认 "error" (mirror Rust: 缺 errorRef 用字面量 "error")
                yield new Error(ref == null ? "error" : ref);
            }
            case "escalation" -> {
                String ref = attrs.get("ruleforge:escalationRef");
                if (ref == null || ref.isBlank()) {
                    throw new FlowExecutionException(
                        "EndEvent endType=escalation requires non-empty ruleforge:escalationRef");
                }
                yield new Escalation(ref);
            }
            case "terminate" -> Terminate.INSTANCE;
            default -> throw new FlowExecutionException(
                "Unknown EndEvent endType=" + endType);
        };
    }
}
