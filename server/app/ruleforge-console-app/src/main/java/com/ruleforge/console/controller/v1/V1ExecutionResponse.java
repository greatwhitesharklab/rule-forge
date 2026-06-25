package com.ruleforge.console.controller.v1;

import com.ruleforge.v1.exec.V1FlowRunner;

import java.util.List;
import java.util.Map;

/**
 * V1 决策流执行结果(POST /v1/execute 响应)。
 *
 * <p>透传 {@link V1FlowRunner.FlowResult}:最终 decision + reject 终止信息 + flags +
 * 执行后的完整 fact(含 ScoreCard 算出的 output、action 写入的字段,供前端展示执行轨迹)。
 */
public class V1ExecutionResponse {
    private final String decision;
    private final boolean rejected;
    private final String rejectReason;
    private final List<Object> flags;
    private final Map<String, Object> fact;

    public V1ExecutionResponse(V1FlowRunner.FlowResult result) {
        this.decision = result.decision;
        this.rejected = result.rejected;
        this.rejectReason = result.rejectReason;
        this.flags = result.flags;
        this.fact = result.fact;
    }

    public String getDecision() {
        return decision;
    }

    public boolean isRejected() {
        return rejected;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public List<Object> getFlags() {
        return flags;
    }

    public Map<String, Object> getFact() {
        return fact;
    }
}
