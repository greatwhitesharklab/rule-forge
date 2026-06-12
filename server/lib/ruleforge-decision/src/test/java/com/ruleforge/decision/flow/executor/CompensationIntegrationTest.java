package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.flow.engine.ConditionEvaluator;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.FlowNodeRunner;
import com.ruleforge.decision.flow.engine.Token;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.34 A3 — Compensation SAGA 端到端 traverse 集成测试。
 *
 * <p>走 FlowNodeRunner.traverse 跑完整 SAGA:start → act → start scope → 内部 act →
 * end scope → throw → handler LIFO → end。验证 vars.compensated 顺序。
 */
@DisplayName("Compensation SAGA 端到端 traverse")
class CompensationIntegrationTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    static class CompensatingAction implements NodeExecutor {
        final Map<String, BiConsumer<FlowNode, FlowContext>> handlers = new HashMap<>();

        CompensatingAction register(String method, BiConsumer<FlowNode, FlowContext> h) {
            handlers.put(method, h);
            return this;
        }

        @Override public String supportedType() { return "SERVICE_TASK:action"; }

        @Override
        public void execute(FlowNode node, FlowContext context) {
            String m = node.attr("ruleforge", "method");
            BiConsumer<FlowNode, FlowContext> h = handlers.get(m);
            if (h == null) return; // noop default
            h.accept(node, context);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("Given 1 嵌套 scope + 2 act 各有 handler,When 跑 traverse,Then handler 按 LIFO 跑")
    void full_traverse_with_compensation_lifo() {
        CompensatingAction action = new CompensatingAction()
            .register("noop", (n, c) -> {})
            .register("mark_main", (n, c) -> {})
            .register("mark_handler_a", (n, c) -> {
                List<String> cur = (List<String>) c.getVars().getOrDefault("compensated", new ArrayList<>());
                List<String> next = new ArrayList<>(cur);
                next.add("ha");
                c.getVars().put("compensated", next);
            })
            .register("mark_handler_b", (n, c) -> {
                List<String> cur = (List<String>) c.getVars().getOrDefault("compensated", new ArrayList<>());
                List<String> next = new ArrayList<>(cur);
                next.add("hb");
                c.getVars().put("compensated", next);
            });

        CompensationStartExecutor cstart = new CompensationStartExecutor();
        CompensationEndExecutor cend = new CompensationEndExecutor();
        CompensationIntermediateExecutor cinter = new CompensationIntermediateExecutor();
        CompensationThrowExecutor cthrow = new CompensationThrowExecutor();
        List<NodeExecutor> list = new ArrayList<>();
        list.add(action);
        list.add(cstart); list.add(cend); list.add(cinter); list.add(cthrow);
        list.add(new EventNodeExecutor());
        list.add(new GatewayNodeExecutor());
        list.add(new UserTaskNodeExecutor());
        list.add(new ScriptNodeExecutor());
        list.add(new MultiInstanceExecutor());
        NodeExecutorRegistry reg = new NodeExecutorRegistry(list);
        CompensationThrowExecutor.Holder.REGISTRY = reg;

        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:ruleforge="http://ruleforge.com/schema">
              <bpmn:process id="p_lifo">
                <bpmn:startEvent id="s"/>
                <bpmn:serviceTask id="act_a" ruleforge:taskType="action" ruleforge:method="mark_main"/>
                <bpmn:compensateStartEvent id="cs" ruleforge:scopeId="s1"/>
                <bpmn:serviceTask id="act_b" ruleforge:taskType="action" ruleforge:method="mark_main"/>
                <bpmn:compensateThrowEvent id="ct"/>
                <bpmn:endEvent id="end"/>
                <bpmn:serviceTask id="ha" ruleforge:taskType="action" ruleforge:method="mark_handler_a"/>
                <bpmn:serviceTask id="hb" ruleforge:taskType="action" ruleforge:method="mark_handler_b"/>
                <bpmn:compensateIntermediateThrowEvent id="ch_a" ruleforge:attachedToRef="act_a">
                  <bpmn:outgoing>fha</bpmn:outgoing>
                </bpmn:compensateIntermediateThrowEvent>
                <bpmn:compensateIntermediateThrowEvent id="ch_b" ruleforge:attachedToRef="act_b">
                  <bpmn:outgoing>fb</bpmn:outgoing>
                </bpmn:compensateIntermediateThrowEvent>
                <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act_a"/>
                <bpmn:sequenceFlow id="e1" sourceRef="act_a" targetRef="cs"/>
                <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="act_b"/>
                <bpmn:sequenceFlow id="e3" sourceRef="act_b" targetRef="ct"/>
                <bpmn:sequenceFlow id="e4" sourceRef="ct" targetRef="end"/>
                <bpmn:sequenceFlow id="fha" sourceRef="ch_a" targetRef="ha"/>
                <bpmn:sequenceFlow id="fb" sourceRef="ch_b" targetRef="hb"/>
                <bpmn:sequenceFlow id="fha2" sourceRef="ha" targetRef="end"/>
                <bpmn:sequenceFlow id="fb2" sourceRef="hb" targetRef="end"/>
              </bpmn:process>
            </bpmn:definitions>
            """;
        FlowDefinition def = parser.parseSingleProcess(xml);

        FlowContext ctx = new FlowContext();
        ctx.setFlowRunId("test-comp-" + System.nanoTime());
        Token t = new Token("tok-" + System.nanoTime());
        t.setCurrentNodeId(def.getStartNodeId());
        ctx.getActiveTokens().add(t);
        ctx.setCurrentToken(t);

        FlowNodeRunner runner = new FlowNodeRunner(reg, new ConditionEvaluator(), null);
        runner.traverse(def, ctx, def.getStartNodeId());

        List<String> compensated = (List<String>) ctx.getVars().get("compensated");
        assertNotNull(compensated, "compensated vars should be set by handler sub-flows");
        // act_a 先注册(早)→ handler_a 后跑;act_b 后注册(晚)→ handler_b 先跑
        assertEquals(Arrays.asList("hb", "ha"), compensated,
            "LIFO order: act_b's handler_b ran first, act_a's handler_a ran second");
        // 跑完 throw 后 stack 应清空
        assertTrue(ctx.getCompensationStack().isEmpty(),
            "stack should be empty after CompensationThrow pops innermost scope");
    }
}
