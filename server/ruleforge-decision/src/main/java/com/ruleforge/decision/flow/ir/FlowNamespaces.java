package com.ruleforge.decision.flow.ir;

/**
 * BPMN 命名空间常量。共享给 IR 解析、节点执行器、sequenceFlow 条件解析。
 */
public final class FlowNamespaces {
    public static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    public static final String FLOWABLE = "http://flowable.org/bpmn";
    public static final String RULEFORGE = "http://ruleforge.com/schema";

    public static final String RF_PREFIX = "ruleforge";
    public static final String FL_PREFIX = "flowable";
    public static final String BPMN_PREFIX = "bpmn";

    private FlowNamespaces() {}
}
