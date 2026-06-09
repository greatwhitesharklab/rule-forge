package com.ruleforge.decision.flow.ir;

/**
 * 决策流节点类型枚举。
 */
public enum NodeType {
    START_EVENT,
    END_EVENT,
    SERVICE_TASK,         // ruleforge:taskType ∈ {rule, action, package, rulesPackage}
    SCRIPT_TASK,
    EXCLUSIVE_GATEWAY,    // ruleforge:decisionType ∈ {condition, percent}
    PARALLEL_GATEWAY,     // 简单 join-all
    USER_TASK,            // ruleforge:decisionType=binary 走人工决策
    INTERMEDIATE_EVENT,   // 暂支持 message/signal 等待
    SUB_PROCESS           // 暂不支持,启动时 warn 不跑
}
