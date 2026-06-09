package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gateway 节点执行器(替代原 DecisionGatewayDelegate)。
 * <p>
 * 决策逻辑:
 * 1. condition:遍历 outgoingFlows,看 UEL 表达式匹配第一条
 * 2. percent:加权和随机选一条
 * 3. default(无 conditionExpression):返回那条
 * 4. 都没匹配 → 抛 FlowExecutionException
 * <p>
 * 注:此 executor 只负责"取值"语义,实际"决定下一节点"在 FlowNodeRunner 里。
 * 这里只做写日志 + 抛异常(若多条同时匹配,先抛错),traverse 由 nextNode() 决定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayNodeExecutor implements NodeExecutor {

    @Override
    public String supportedType() {
        return "EXCLUSIVE_GATEWAY";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        // Gateway 节点本身不做事,实际路由在 FlowNodeRunner.nextNode() 里。
        // 这里只校验:userTask 后的 binary 决策字段在 ctx.vars 里存在
        if (context.getCurrentAwaitingField() != null
            && node.getType() == com.ruleforge.decision.flow.ir.NodeType.USER_TASK) {
            if (!context.getVars().containsKey(context.getCurrentAwaitingField())) {
                throw new FlowExecutionException(
                    "UserTask awaiting decision but ctx.vars missing field="
                    + context.getCurrentAwaitingField());
            }
        }
    }
}
