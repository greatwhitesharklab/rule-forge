package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;

/**
 * 节点执行器接口。所有实现 stateless,可并发。
 *
 * @throws AsyncNodeSuspendException 异步挂起 — FlowNodeRunner 收到后写状态表 + return
 * @throws FlowExecutionException    致命错误 — FlowNodeRunner 收到后写 FAILED + 抛出
 */
public interface NodeExecutor {
    /** 节点类型,NodeExecutorRegistry 据此路由 */
    String supportedType();

    /**
     * @param node 当前节点 IR
     * @param context 执行上下文(共享 vars / session / outputModel)
     */
    void execute(FlowNode node, FlowContext context) throws Exception;
}
