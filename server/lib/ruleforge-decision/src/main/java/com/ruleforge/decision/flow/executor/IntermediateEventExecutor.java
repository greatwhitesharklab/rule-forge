package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.IntermediateEventKind;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * V5.35 A5 — IntermediateEvent 节点执行器(7-path dispatcher)。
 *
 * <p>Mirror Rust V5.32 {@code intermediate_event.rs} 7 variant 契约:
 * <ul>
 *   <li>{@link IntermediateEventKind.None} — 透传 Continue(不抛,Runner 走默认 out)</li>
 *   <li>{@link IntermediateEventKind.Message} — 抛 AsyncNodeSuspendException(waitType=ASYNC_DATA, waitRef=message:&lt;name&gt;)</li>
 *   <li>{@link IntermediateEventKind.Signal} — 抛 Suspend(waitType=ASYNC_DATA, waitRef=signal:&lt;name&gt;)</li>
 *   <li>{@link IntermediateEventKind.Timer} — 抛 Suspend(waitType=ASYNC_TASK, nextRetryAt=now+duration)</li>
 *   <li>{@link IntermediateEventKind.Conditional} — 抛 Suspend(waitType=ASYNC_DATA, waitRef=conditional:&lt;nodeId&gt;, payload.condition=expr)</li>
 *   <li>{@link IntermediateEventKind.LinkThrow} — 抛 {@link BranchTransition}(targetNodeId=linkCatch),由 Runner traverse 读</li>
 *   <li>{@link IntermediateEventKind.LinkCatch} — 透传 Continue</li>
 * </ul>
 *
 * <p>LinkThrow 需要当前 {@link FlowDefinition} 来查 linkTargets 索引。
 * 来源(优先级):
 * <ol>
 *   <li>{@code ctx.getCurrentDef()} — Runner traverse 在 dispatch 前 set</li>
 *   <li>静态 fallback {@link Holder#DEF} — 测试场景手工 for-loop 时用</li>
 * </ol>
 */
@Slf4j
@Component
public class IntermediateEventExecutor implements NodeExecutor {

    /** V5.35 A5 — 测试场景 fallback:静态 def holder。Spring 环境优先走 ctx.currentDef。 */
    public static class Holder {
        public static volatile FlowDefinition DEF;
    }

    /**
     * V5.35 A5 — LinkThrow 用此 sealed exception 把"跳到 linkCatch"信号透给 Runner traverse。
     * Runner catch 后转译成 {@code NodeTransition.Kind.BRANCH},跳过 throw 出边。
     */
    public static final class BranchTransition extends RuntimeException {
        private final String targetNodeId;
        public BranchTransition(String targetNodeId) {
            super("IntermediateLinkThrow → branch to " + targetNodeId);
            this.targetNodeId = targetNodeId;
        }
        public String targetNodeId() { return targetNodeId; }
    }

    @Override
    public String supportedType() {
        return "INTERMEDIATE_EVENT";
    }

    @Override
    public void execute(FlowNode node, FlowContext context) {
        IntermediateEventKind kind = IntermediateEventKind.fromAttrs(node.getExtensionAttrs());
        if (kind instanceof IntermediateEventKind.None) {
            // 透传 Continue
            return;
        }
        if (kind instanceof IntermediateEventKind.Message m) {
            throw suspendMessage(node, context, m.name());
        }
        if (kind instanceof IntermediateEventKind.Signal s) {
            throw suspendSignal(node, context, s.name());
        }
        if (kind instanceof IntermediateEventKind.Timer t) {
            throw suspendTimer(node, context, t.duration());
        }
        if (kind instanceof IntermediateEventKind.Conditional c) {
            throw suspendConditional(node, context, c.expr());
        }
        if (kind instanceof IntermediateEventKind.LinkThrow lt) {
            String target = resolveLinkTarget(context, lt.linkName());
            log.info("[LINK-THROW] flowRunId={} from={} to={} linkName={}",
                context.getFlowRunId(), node.getNodeId(), target, lt.linkName());
            throw new BranchTransition(target);
        }
        if (kind instanceof IntermediateEventKind.LinkCatch lc) {
            // 透传 Continue(也校验 linkName 在 linkTargets 里有对应 catch 节点,防止误连)
            FlowDefinition def = resolveDef(context);
            if (def != null && !def.getLinkTargets().containsKey(lc.linkName())) {
                log.warn("[LINK-CATCH-ORPHAN] flowRunId={} node={} linkName={} has no matching linkThrow (continuing anyway)",
                    context.getFlowRunId(), node.getNodeId(), lc.linkName());
            }
            return;
        }
        // 不可达(sealed interface exhaustive)
        throw new FlowExecutionException("Unhandled IntermediateEventKind: " + kind.getClass().getName());
    }

    private AsyncNodeSuspendException suspendMessage(FlowNode node, FlowContext ctx, String name) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "message");
        payload.put("eventName", name);
        log.info("[INTERMEDIATE-SUSPEND-MESSAGE] flowRunId={} nodeId={} eventName={}",
            ctx.getFlowRunId(), node.getNodeId(), name);
        return new AsyncNodeSuspendException(
            node.getNodeId(),
            "INTERMEDIATE_EVENT",
            AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA,
            "message:" + name,
            payload,
            null);
    }

    private AsyncNodeSuspendException suspendSignal(FlowNode node, FlowContext ctx, String name) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "signal");
        payload.put("eventName", name);
        log.info("[INTERMEDIATE-SUSPEND-SIGNAL] flowRunId={} nodeId={} eventName={}",
            ctx.getFlowRunId(), node.getNodeId(), name);
        return new AsyncNodeSuspendException(
            node.getNodeId(),
            "INTERMEDIATE_EVENT",
            AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA,
            "signal:" + name,
            payload,
            null);
    }

    private AsyncNodeSuspendException suspendTimer(FlowNode node, FlowContext ctx, java.time.Duration duration) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "timer");
        payload.put("duration", duration.toString());
        Instant nextRetry = Instant.now().plus(duration);
        log.info("[INTERMEDIATE-SUSPEND-TIMER] flowRunId={} nodeId={} duration={} nextRetryAt={}",
            ctx.getFlowRunId(), node.getNodeId(), duration, nextRetry);
        return new AsyncNodeSuspendException(
            node.getNodeId(),
            "INTERMEDIATE_EVENT",
            AsyncNodeSuspendException.WAIT_TYPE_ASYNC_TASK,
            "timer:" + node.getNodeId(),
            payload,
            nextRetry);
    }

    private AsyncNodeSuspendException suspendConditional(FlowNode node, FlowContext ctx, String expr) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventType", "conditional");
        payload.put("condition", expr);
        log.info("[INTERMEDIATE-SUSPEND-CONDITIONAL] flowRunId={} nodeId={} expr={}",
            ctx.getFlowRunId(), node.getNodeId(), expr);
        return new AsyncNodeSuspendException(
            node.getNodeId(),
            "INTERMEDIATE_EVENT",
            AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA,
            "conditional:" + node.getNodeId(),
            payload,
            null);
    }

    private String resolveLinkTarget(FlowContext ctx, String linkName) {
        FlowDefinition def = resolveDef(ctx);
        if (def == null) {
            throw new FlowExecutionException(
                "IntermediateEvent LinkThrow needs FlowDefinition (ctx.currentDef is null); "
                + "ensure Runner.traverse sets currentDef before dispatch. linkName=" + linkName);
        }
        String target = def.getLinkTargets().get(linkName);
        if (target == null) {
            throw new FlowExecutionException(
                "IntermediateEvent LinkThrow linkName=" + linkName + " has no matching linkCatch "
                + "(def.linkTargets=" + def.getLinkTargets() + ")");
        }
        return target;
    }

    private FlowDefinition resolveDef(FlowContext ctx) {
        FlowDefinition def = ctx.getCurrentDef();
        if (def == null) def = Holder.DEF;
        return def;
    }
}
