package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Decision 节点 — 流程出口(endEvent)。**不自己算决策**:决策值由上游 SET_DECISION
 * action 写入 {@link #decisionField}(默认 "decision")。本节点职责:
 * <ol>
 *   <li>校验 decisionField 值 ∈ {@link #outputs}(否则报错)</li>
 *   <li>把该值作为流程最终结果 emit</li>
 *   <li>decisionField 未被设置 → 用 {@link #defaultOutput}(或报错)</li>
 * </ol>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionNode extends NodeBase {
    /** 允许的最终结果集合,如 ["approve","review","reject"]。 */
    private List<String> outputs;
    /** 读取哪个 fact 字段作为决策值,默认 "decision"。 */
    private String decisionField;
    /** decisionField 未被设置时的兜底值。 */
    private String defaultOutput;

    @Override
    public String getType() {
        return "Decision";
    }

    public List<String> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<String> outputs) {
        this.outputs = outputs;
    }

    public String getDecisionField() {
        return decisionField;
    }

    public void setDecisionField(String decisionField) {
        this.decisionField = decisionField;
    }

    public String getDefaultOutput() {
        return defaultOutput;
    }

    public void setDefaultOutput(String defaultOutput) {
        this.defaultOutput = defaultOutput;
    }
}
