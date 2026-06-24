package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * V1 结构化 Action。**永远不含 CEL**(硬约束 — 见 design doc Block 4)。
 *
 * <p>{@code value} 是字面量(number/string/boolean);需要引用其它字段值时用 {@code ref}
 * (只读字段引用,不接 CEL 表达式)。{@code value} 与 {@code ref} 互斥。
 *
 * <p>JSON 示例:
 * <pre>
 * { "type": "SET_VARIABLE", "target": "rate", "value": 0.18 }
 * { "type": "SET_VARIABLE", "target": "rate", "ref": "baseRate" }
 * { "type": "REJECT", "reason": "BLACKLIST" }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Action {
    private ActionType type;
    /** 写入的 fact 字段名(SET_VARIABLE / ADD_SCORE 必填)。 */
    private String target;
    /** 字面量值(number/string/boolean)。与 {@link #ref} 互斥。 */
    private Object value;
    /** 只读字段引用,如 "riskScore"。与 {@link #value} 互斥。 */
    private String ref;
    /** 审计理由(SET_DECISION / REJECT / FLAG 常用)。 */
    private String reason;

    public Action() {
    }

    public Action(ActionType type) {
        this.type = type;
    }

    public ActionType getType() {
        return type;
    }

    public void setType(ActionType type) {
        this.type = type;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
