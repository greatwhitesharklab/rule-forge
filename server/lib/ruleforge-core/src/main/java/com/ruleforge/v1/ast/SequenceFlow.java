package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * BPMN sequenceFlow(顺序流/边)。sourceRef→targetRef 引用元素 id。
 * {@link #conditionExpression}(CEL)仅 gateway 出边评估,普通出边无条件。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SequenceFlow extends FlowElement {
    private String sourceRef;
    private String targetRef;
    /** CEL 表达式,仅 gateway 出边评估。 */
    private String conditionExpression;

    @Override
    public String getType() {
        return "sequenceFlow";
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getTargetRef() {
        return targetRef;
    }

    public void setTargetRef(String targetRef) {
        this.targetRef = targetRef;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }
}
