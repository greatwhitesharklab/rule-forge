package com.ruleforge.decision.exception;

/**
 * 决策流执行中的致命异常。FlowNodeRunner 看到后写 nd_decision_flow_state.status=FAILED。
 */
public class FlowExecutionException extends RuntimeException {
    public FlowExecutionException(String message) {
        super(message);
    }

    public FlowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
