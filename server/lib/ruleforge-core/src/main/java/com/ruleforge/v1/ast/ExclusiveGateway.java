package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * BPMN exclusiveGateway(排他网关,二选一/多选一)。出边带 {@link SequenceFlow#getConditionExpression()}
 * (CEL);无 condition 命中时走 {@link #defaultFlow} 兜底。
 *
 * <p>MVP 线性流程可不用,V1 完整版才需要(分流)。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExclusiveGateway extends FlowElement {
    /** 无 condition 命中时走的出边 id。 */
    private String defaultFlow;

    @Override
    public String getType() {
        return "exclusiveGateway";
    }

    public String getDefaultFlow() {
        return defaultFlow;
    }

    public void setDefaultFlow(String defaultFlow) {
        this.defaultFlow = defaultFlow;
    }
}
