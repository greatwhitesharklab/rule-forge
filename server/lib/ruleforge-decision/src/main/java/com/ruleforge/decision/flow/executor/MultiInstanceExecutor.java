package com.ruleforge.decision.flow.executor;

import com.ruleforge.Utils;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V5.33 A1 — Multi-Instance loopCharacteristics wrapper。
 *
 * <p>Mirror Rust V5.29 契约(`experiments/server-rust/crates/rf-executor/src/executors/multi_instance.rs`)。
 *
 * <p>行为:
 * <ul>
 *   <li>`ruleforge:multiInstance="true"` 触发 wrapper,任何 task kind(serviceTask / scriptTask / userTask)适用</li>
 *   <li>`ruleforge:collection` = vars 里数组的 key(必须已存在,类型 List)</li>
 *   <li>`ruleforge:elementVar` = 每轮 element 写入 vars 的 key</li>
 *   <li>`ruleforge:outputVariable`(可选)= 把每轮 elementVar 值收成 List 写回 parent.vars</li>
 *   <li>`ruleforge:multiInstanceSequential="true"` 串行(默认 parallel)</li>
 *   <li>空 collection → Continue,不跑 inner</li>
 *   <li>inner Suspend 透传</li>
 *   <li>parallel parent-wins 碰撞;child 写新 key 落 parent</li>
 *   <li>sequential 写累积,后写胜出</li>
 * </ul>
 *
 * <p>注册:`SERVICE_TASK:multiInstance` 槽,`NodeExecutorRegistry` 在 SERVICE_TASK
 * resolve 路径上检查 `multiInstance` attr,优先路由到这里。
 *
 * <p>Inner executor 通过 registry 拿 — 解决循环依赖(wrapper Bean 依赖 registry,
 * registry 收集所有 executors):wrapper 实例**不持** registry 引用,改为
 * <ol>
 *   <li>Spring 环境:`Utils.getApplicationContext().getBean(NodeExecutorRegistry.class)`</li>
 *   <li>测试环境:`MultiInstanceExecutor.Holder.REGISTRY`(静态 fallback)</li>
 * </ol>
 */
@Slf4j
@Component
public class MultiInstanceExecutor implements NodeExecutor {

    /** V5.33 A1 — 测试场景 fallback:静态 registry holder。Spring 环境优先走 ApplicationContext。 */
    public static class Holder {
        public static volatile NodeExecutorRegistry REGISTRY;
    }

    public MultiInstanceExecutor() {
        // no-op 构造 — registry 延迟到 execute() 时拿
    }

    @Override
    public String supportedType() {
        return "SERVICE_TASK:multiInstance";
    }

    @Override
    public void execute(FlowNode node, FlowContext ctx) throws Exception {
        // 1. 读 attrs
        String isSequentialRaw = node.attr("ruleforge", "multiInstanceSequential");
        boolean isSequential = "true".equalsIgnoreCase(isSequentialRaw);
        String collectionVar = node.attr("ruleforge", "collection");
        String elementVar = node.attr("ruleforge", "elementVar");
        String outputVar = node.attr("ruleforge", "outputVariable");

        if (collectionVar == null || collectionVar.isBlank()) {
            throw new FlowExecutionException(
                "multi-instance task missing ruleforge:collection at node " + node.getNodeId());
        }
        if (elementVar == null || elementVar.isBlank()) {
            throw new FlowExecutionException(
                "multi-instance task missing ruleforge:elementVar at node " + node.getNodeId());
        }

        // 2. 读 collection(必须是 List)
        Object collectionObj = ctx.getVars().get(collectionVar);
        if (!(collectionObj instanceof List)) {
            String actualType = collectionObj == null
                ? "null"
                : collectionObj.getClass().getSimpleName();
            throw new FlowExecutionException(
                "multi-instance collection '" + collectionVar
                + "' is not an array (got " + actualType + ") at node " + node.getNodeId());
        }
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) collectionObj;

        // 3. 空 collection
        if (items.isEmpty()) {
            if (outputVar != null && !outputVar.isBlank()) {
                ctx.getVars().put(outputVar, new ArrayList<>());
            }
            log.debug("[MI-EMPTY] nodeId={} collection={} → Continue",
                node.getNodeId(), collectionVar);
            return;
        }

        // 4. sequential or parallel
        if (isSequential) {
            runSequential(node, ctx, items, elementVar, outputVar);
        } else {
            runParallelInline(node, ctx, items, elementVar, outputVar);
        }
    }

    /**
     * Sequential — 同一 ctx 跑 N 次,vars 累积;后写胜出。
     * elementVar 每轮覆写;inner 写的非 elementVar 永久落 parent。
     */
    private void runSequential(FlowNode node, FlowContext ctx, List<Object> items,
                               String elementVar, String outputVar) throws Exception {
        NodeExecutor inner = resolveInner(node);
        List<Object> outputs = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            ctx.getVars().put(elementVar, item);
            inner.execute(node, ctx);  // Suspend 透传给 caller(Runner catch)
            if (outputVar != null && !outputVar.isBlank()) {
                outputs.add(ctx.getVars().get(elementVar));
            }
            log.debug("[MI-SEQ] nodeId={} iter={}/{} item={}",
                node.getNodeId(), i + 1, items.size(), item);
        }
        if (outputVar != null && !outputVar.isBlank()) {
            ctx.getVars().put(outputVar, outputs);
        }
    }

    /**
     * Parallel — clone parent vars 跑 N 次;parent-wins 碰撞;child 写新 key 落 parent;
     * elementVar 末班胜出(同 Rust `assign(elementVar, last)`)。
     */
    private void runParallelInline(FlowNode node, FlowContext ctx, List<Object> items,
                                   String elementVar, String outputVar) throws Exception {
        NodeExecutor inner = resolveInner(node);
        Map<String, Object> parentVars = ctx.getVars();
        Set<String> parentKeys = new HashSet<>(parentVars.keySet());
        List<Object> allOutputs = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            // fresh child vars = parent clone + elementVar 覆写
            Map<String, Object> childVars = new HashMap<>(parentVars);
            childVars.put(elementVar, item);

            // 替身 ctx:getVars/setVars 走 childVars(不污染 parent.currentToken)
            FlowContext childCtx = new MultiInstanceChildContext(ctx, childVars);
            inner.execute(node, childCtx);  // Suspend 透传

            // collect_child_writes:只收 child 写的新 key,parent-wins 同 key
            for (Map.Entry<String, Object> e : childVars.entrySet()) {
                String k = e.getKey();
                if (k.equals(elementVar)) continue;  // elementVar 由 wrapper 单独处理
                if (!parentKeys.contains(k)) {
                    parentVars.put(k, e.getValue());
                }
            }
            if (outputVar != null && !outputVar.isBlank()) {
                allOutputs.add(childVars.get(elementVar));
            }
            log.debug("[MI-PAR] nodeId={} iter={}/{} item={}",
                node.getNodeId(), i + 1, items.size(), item);
        }
        if (outputVar != null && !outputVar.isBlank()) {
            parentVars.put(outputVar, allOutputs);
        }
        // 末班胜出 elementVar(同 Rust `assign(elementVar, items.last())`)
        parentVars.put(elementVar, items.get(items.size() - 1));
    }

    /**
     * 解析 inner executor — 优先 Spring,fallback Holder.REGISTRY。
     * resolve 传入 wrapper 节点本身,registry 会"递归"用 wrapper 自身造成无限循环;
     * 解决:临时剥掉 multiInstance attr,再调一次 resolve。
     */
    private NodeExecutor resolveInner(FlowNode node) {
        NodeExecutorRegistry reg = resolveRegistry();
        // 临时剥掉 multiInstance attr,避免 registry 路由回 wrapper
        FlowNode innerNode = node.withoutAttr("ruleforge:multiInstance");
        return reg.resolve(innerNode);
    }

    private NodeExecutorRegistry resolveRegistry() {
        try {
            return Utils.getApplicationContext().getBean(NodeExecutorRegistry.class);
        } catch (Exception e) {
            NodeExecutorRegistry holder = Holder.REGISTRY;
            if (holder == null) {
                throw new FlowExecutionException(
                    "MultiInstanceExecutor: NodeExecutorRegistry not available "
                    + "(neither Spring context nor Holder.REGISTRY)", e);
            }
            return holder;
        }
    }
}
