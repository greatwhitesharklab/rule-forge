package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * V5.34 A3 — {@code <bpmn:compensateThrowEvent/>} 节点执行器。
 *
 * <p>Mirror Rust V5.31 P0 {@code compensation_throw.rs} 契约:
 * 1. 调 {@link CompensationRunner#runHandlers} 共享 helper
 * 2. 空 stack → 抛 {@code FlowExecutionException("CompensationNoScope")}
 * 3. handler 失败 → log + 累积到 trace.failures,继续下一个
 * 4. handler suspend → 透传 {@link AsyncNodeSuspendException}(外层 traverse catch 写 WAITING_CALLBACK)
 *
 * <p>关键设计:Java 端 {@link NodeExecutor#execute(FlowNode, FlowContext)} 拿不到 registry。
 * 采用 V5.33 A1 {@code MultiInstanceExecutor.Holder.REGISTRY} 同套路(静态 fallback + Spring primary)
 * 拿 {@link NodeExecutorRegistry},再用 registry.resolve + traverse 跑 sub-flow(在
 * {@link CompensationRunner} 内部已经用 sub-traverse 跑 sub-flow,所以 CompensationThrow
 * 只需要拿 registry 给 Runner 用即可)。
 */
@Slf4j
@Component
public class CompensationThrowExecutor implements NodeExecutor, ApplicationContextAware {

    @Override
    public String supportedType() {
        return "COMPENSATION_THROW";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        log.info("[COMP-THROW] flowRunId={} nodeId={} — running handlers",
            context.getFlowRunId(), node.getNodeId());
        NodeExecutorRegistry reg = resolveRegistry();
        if (reg == null) {
            throw new com.ruleforge.decision.exception.FlowExecutionException(
                "CompensationThrow requires NodeExecutorRegistry; "
                + "neither Holder.REGISTRY nor Spring context available. "
                + "Did you forget to construct a registry in your test?");
        }
        CompensationRunner.runHandlers(getCurrentDef(context), context, reg);
    }

    /**
     * 拿当前流程的 {@link com.ruleforge.decision.flow.ir.FlowDefinition}。
     * 测试场景:由 Runner 在 traverse 前 set;production 场景:由 Runner 在 traverse 时注入。
     */
    private com.ruleforge.decision.flow.ir.FlowDefinition getCurrentDef(FlowContext ctx) {
        if (ctx.getCurrentDef() != null) return ctx.getCurrentDef();
        // 兜底:Holder.DEF(测试场景)
        return Holder.DEF;
    }

    private NodeExecutorRegistry resolveRegistry() {
        if (Holder.REGISTRY != null) return Holder.REGISTRY;
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(NodeExecutorRegistry.class);
            } catch (BeansException ignore) {}
        }
        return null;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    private ApplicationContext applicationContext;

    /**
     * V5.33 A1 / V5.34 A3 模式:测试场景下由测试代码显式注入 registry +
     * current def;production 走 Spring primary bean。
     */
    public static final class Holder {
        public static NodeExecutorRegistry REGISTRY;
        public static com.ruleforge.decision.flow.ir.FlowDefinition DEF;
        private Holder() {}
    }
}
