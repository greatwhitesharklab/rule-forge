package com.ruleforge.decision.exception;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * 异步节点挂起协议。
 * <p>
 * 节点执行器需要"等外部信号"时抛此异常。FlowNodeRunner 看到后:
 * 1. 写 nd_decision_flow_state 一行(WAITING_CALLBACK / PENDING_ASYNC)
 * 2. 把 ctx.vars 序列化到 row_vars
 * 3. 立刻 return — DecisionServiceImpl 走 savePendingLog + DecisionResponse.asyncPending
 * <p>
 * 三种 waitType 协议:
 * - USER_TASK: 人工决策 0/1(0 = 无超时,业务方主动 POST /flow/decision 恢复)
 * - ASYNC_DATA: 异步数据源(包装现 AsyncDataSourcePendingException)
 * - ASYNC_TASK: 自定义异步任务(ruleforge:async="true" 节点)
 */
public final class AsyncNodeSuspendException extends RuntimeException {
    public static final String WAIT_TYPE_USER_TASK = "USER_TASK";
    public static final String WAIT_TYPE_ASYNC_DATA = "ASYNC_DATA";
    public static final String WAIT_TYPE_ASYNC_TASK = "ASYNC_TASK";

    private final String currentNodeId;
    private final String currentNodeType;
    private final String waitType;
    private final String waitRef;
    private final Map<String, Object> payload;
    private final Instant nextRetryAt;

    public AsyncNodeSuspendException(String currentNodeId, String currentNodeType,
                                     String waitType, String waitRef,
                                     Map<String, Object> payload, Instant nextRetryAt) {
        super("AsyncNodeSuspend: nodeId=" + currentNodeId + " waitType=" + waitType + " waitRef=" + waitRef);
        this.currentNodeId = currentNodeId;
        this.currentNodeType = currentNodeType;
        this.waitType = waitType;
        this.waitRef = waitRef;
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
        this.nextRetryAt = nextRetryAt;
    }

    public String getCurrentNodeId() { return currentNodeId; }
    public String getCurrentNodeType() { return currentNodeType; }
    public String getWaitType() { return waitType; }
    public String getWaitRef() { return waitRef; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getNextRetryAt() { return nextRetryAt; }
}
