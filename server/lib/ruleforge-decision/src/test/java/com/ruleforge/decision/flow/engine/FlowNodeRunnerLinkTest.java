package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.executor.ActionNodeExecutor;
import com.ruleforge.decision.flow.executor.EventNodeExecutor;
import com.ruleforge.decision.flow.executor.GatewayNodeExecutor;
import com.ruleforge.decision.flow.executor.IntermediateEventExecutor;
import com.ruleforge.decision.flow.executor.NodeExecutor;
import com.ruleforge.decision.flow.executor.NodeExecutorRegistry;
import com.ruleforge.decision.flow.executor.ParallelGatewayExecutor;
import com.ruleforge.decision.flow.executor.UserTaskNodeExecutor;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.35 A5 — FlowNodeRunner.traverse link throw→catch 跳转集成测试。
 *
 * <p>Mirror Rust V5.32 {@code intermediate_event.rs} + traverse behavior:
 * <ul>
 *   <li>linkThrow 跳到 linkCatch(跳过 throw 出边)</li>
 *   <li>linkThrow 找不到 linkCatch → 抛错</li>
 * </ul>
 *
 * <p>关键点:Runner traverse 识别 NodeTransition.Kind.BRANCH,直接跳到 branch.targetNodeId
 * (类似 FORK 推 N 个 sub-token),不读 throw 的 outgoing。
 */
@DisplayName("FlowNodeRunner link throw→catch 跳转")
class FlowNodeRunnerLinkTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    static class MultiMethodActionExecutor implements NodeExecutor {
        final java.util.Map<String, java.util.function.BiConsumer<com.ruleforge.decision.flow.ir.FlowNode, FlowContext>> handlers
            = new java.util.HashMap<>();

        MultiMethodActionExecutor register(String method,
                                            java.util.function.BiConsumer<com.ruleforge.decision.flow.ir.FlowNode, FlowContext> h) {
            handlers.put(method, h);
            return this;
        }

        @Override public String supportedType() { return "SERVICE_TASK:action"; }
        @Override
        public void execute(com.ruleforge.decision.flow.ir.FlowNode node, FlowContext context) {
            String m = node.attr("ruleforge", "method");
            java.util.function.BiConsumer<com.ruleforge.decision.flow.ir.FlowNode, FlowContext> h = handlers.get(m);
            if (h == null) throw new IllegalStateException("No handler for method=" + m);
            h.accept(node, context);
        }
    }

    private FlowNodeRunner newRunner(MultiMethodActionExecutor action) {
        List<NodeExecutor> list = new ArrayList<>();
        list.add(action);
        list.add(new IntermediateEventExecutor());
        list.add(new EventNodeExecutor());
        list.add(new GatewayNodeExecutor());
        list.add(new ParallelGatewayExecutor());
        list.add(new UserTaskNodeExecutor());
        NodeExecutorRegistry registry = new NodeExecutorRegistry(list);
        return new FlowNodeRunner(registry, new ConditionEvaluator(), null);
    }

    private FlowContext newCtx(String id) {
        FlowContext ctx = new FlowContext();
        ctx.setFlowRunId(id);
        return ctx;
    }

    @Test
    @DisplayName("Given linkThrow→linkCatch (同名 midway),When traverse,Then 跳到 catch 节点,不执行 throw 出边上的节点")
    void link_throw_routes_to_link_catch_skipping_throw_outgoing() {
        AtomicBoolean throwOutRan = new AtomicBoolean(false);
        AtomicReference<String> catchReached = new AtomicReference<>();
        AtomicReference<String> postCatchReached = new AtomicReference<>();

        MultiMethodActionExecutor action = new MultiMethodActionExecutor()
            // 'act_throw' 是 throw 出边上的节点,**应该被跳过**
            .register("act_throw", (n, c) -> throwOutRan.set(true))
            .register("act_catch", (n, c) -> catchReached.set("at_catch"))
            .register("act_post", (n, c) -> postCatchReached.set("at_post"));

        // 拓扑:
        //   s → lt(linkThrow midway) ─out→ act_throw → end_a(死)
        //   lc(linkCatch midway) → act_catch → act_post → end_b
        // 期望:throw 出边 act_throw 不跑;走 linkCatch 分支
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:ruleforge="http://ruleforge.com/schema">
              <bpmn:process id="p1">
                <bpmn:startEvent id="s"/>
                <bpmn:intermediateCatchEvent id="lt"
                    ruleforge:eventType="linkThrow" ruleforge:linkName="midway"/>
                <bpmn:serviceTask id="act_throw" ruleforge:taskType="action" ruleforge:method="act_throw"/>
                <bpmn:endEvent id="end_a"/>
                <bpmn:intermediateCatchEvent id="lc"
                    ruleforge:eventType="linkCatch" ruleforge:linkName="midway"/>
                <bpmn:serviceTask id="act_catch" ruleforge:taskType="action" ruleforge:method="act_catch"/>
                <bpmn:serviceTask id="act_post" ruleforge:taskType="action" ruleforge:method="act_post"/>
                <bpmn:endEvent id="end_b"/>
                <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="lt"/>
                <bpmn:sequenceFlow id="e1" sourceRef="lt" targetRef="act_throw"/>
                <bpmn:sequenceFlow id="e2" sourceRef="act_throw" targetRef="end_a"/>
                <bpmn:sequenceFlow id="e3" sourceRef="lc" targetRef="act_catch"/>
                <bpmn:sequenceFlow id="e4" sourceRef="act_catch" targetRef="act_post"/>
                <bpmn:sequenceFlow id="e5" sourceRef="act_post" targetRef="end_b"/>
              </bpmn:process>
            </bpmn:definitions>
            """;
        FlowDefinition def = parser.parseSingleProcess(xml);
        FlowNodeRunner runner = newRunner(action);
        FlowContext ctx = newCtx("link-ok");

        runner.traverse(def, ctx, def.getStartNodeId());

        assertEquals("at_catch", catchReached.get(), "linkCatch branch should have run act_catch");
        assertEquals("at_post", postCatchReached.get(), "post-catch should have run");
        // throw 出边 act_throw **不**应该跑
        assertTrue(!throwOutRan.get(),
            "linkThrow outgoing (act_throw) should be skipped; got ran=" + throwOutRan.get());
    }

    @Test
    @DisplayName("Given linkThrow 找不到同名 linkCatch,When traverse,Then 抛 FlowExecutionException")
    void link_throw_with_unmatched_link_name_throws() {
        MultiMethodActionExecutor action = new MultiMethodActionExecutor();

        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:ruleforge="http://ruleforge.com/schema">
              <bpmn:process id="p1">
                <bpmn:startEvent id="s"/>
                <bpmn:intermediateCatchEvent id="lt"
                    ruleforge:eventType="linkThrow" ruleforge:linkName="ghost"/>
                <bpmn:endEvent id="end"/>
                <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="lt"/>
                <bpmn:sequenceFlow id="e1" sourceRef="lt" targetRef="end"/>
              </bpmn:process>
            </bpmn:definitions>
            """;
        FlowDefinition def = parser.parseSingleProcess(xml);
        FlowNodeRunner runner = newRunner(action);
        FlowContext ctx = newCtx("link-missing");

        FlowExecutionException ex = org.junit.jupiter.api.Assertions.assertThrows(
            FlowExecutionException.class,
            () -> runner.traverse(def, ctx, def.getStartNodeId()));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase().contains("link")
                || ex.getMessage().toLowerCase().contains("ghost"),
            "msg should mention link/ghost, got: " + ex.getMessage());
    }
}
