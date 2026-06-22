package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
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
 * <p>V6.13.4c: 套 V5.33 A1 {@link MultiInstanceExecutor} 模式 — 构造注入 {@link BeanFactory}
 * 替代 {@code ApplicationContextAware} static lookup。{@code Holder.REGISTRY} 静态 fallback
 * 仍保留(测试场景无 BeanFactory)。
 */
@Slf4j
@Component
public class CompensationThrowExecutor implements NodeExecutor {

    private final BeanFactory beanFactory;

    /**
     * Spring ctor — production 路径走 BeanFactory 解析 registry。
     */
    public CompensationThrowExecutor(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * 无参 ctor — 测试场景由 {@link Holder#REGISTRY} 显式注入 registry,
     * 跟 V5.33 A1 {@code MultiInstanceExecutor} 同模式。
     */
    public CompensationThrowExecutor() {
        this.beanFactory = null;
    }

    @Override
    public String supportedType() {
        return "COMPENSATION_THROW";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        log.info("[COMP-THROW] flowRunId={} nodeId={} — running handlers",
            context.identity().flowRunId(), node.getNodeId());
        NodeExecutorRegistry reg = resolveRegistry();
        if (reg == null) {
            throw new com.ruleforge.decision.exception.FlowExecutionException(
                "CompensationThrow requires NodeExecutorRegistry; "
                + "neither Holder.REGISTRY nor Spring BeanFactory available. "
                + "Did you forget to construct a registry in your test?");
        }
        CompensationRunner.runHandlers(getCurrentDef(context), context, reg);
    }

    /**
     * 拿当前流程的 {@link com.ruleforge.decision.flow.ir.FlowDefinition}。
     * 测试场景:由 Runner 在 traverse 前 set;production 场景:由 Runner 在 traverse 时注入。
     */
    private com.ruleforge.decision.flow.ir.FlowDefinition getCurrentDef(FlowContext ctx) {
        if (ctx.currentDef() != null) return ctx.currentDef();
        // 兜底:Holder.DEF(测试场景)
        return Holder.DEF;
    }

    private NodeExecutorRegistry resolveRegistry() {
        if (Holder.REGISTRY != null) return Holder.REGISTRY;
        if (beanFactory != null) {
            try {
                return beanFactory.getBean(NodeExecutorRegistry.class);
            } catch (Exception ignore) {}
        }
        return null;
    }

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
