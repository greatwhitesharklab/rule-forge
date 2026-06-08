package com.ruleforge.decision.exception;

/**
 * DecisionServiceImpl.evaluate 看到的"决策流已挂起"信号。
 * <p>
 * FlowEngine.start 返回 status=WAITING_CALLBACK / PENDING_ASYNC 时,
 * executeDecisionFlow 抛此异常;外层 catch 后调 savePendingLog + 返回 DecisionResponse.asyncPending。
 */
public class DecisionAsyncPendingException extends RuntimeException {
    private final String waitRef;
    private final String currentNodeId;
    private final String waitType;

    public DecisionAsyncPendingException(String waitRef, String currentNodeId, String waitType) {
        super("Decision flow pending: waitRef=" + waitRef + " node=" + currentNodeId);
        this.waitRef = waitRef;
        this.currentNodeId = currentNodeId;
        this.waitType = waitType;
    }

    public String getWaitRef() { return waitRef; }
    public String getCurrentNodeId() { return currentNodeId; }
    public String getWaitType() { return waitType; }
}
