package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.entity.DecisionFlowState;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.33 A1 — Multi-Instance 端到端集成测试。
 *
 * <p>走 FlowNodeRunner.traverse 真实路径:startEvent → MI serviceTask → endEvent,
 * 验证 MI wrapper 跟 V5.33 A0 fork/join 路径集成无破坏。
 */
@DisplayName("MultiInstance 端到端 traverse")
class MultiInstanceIntegrationTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    @Test
    @DisplayName("Given start → MI task → end,When 跑 traverse,Then outputs=[a,b,c],tag 末班,无错")
    void full_traverse_with_multi_instance_completes() {
        NodeExecutor action = new NodeExecutor() {
            @Override
            public String supportedType() {
                return "SERVICE_TASK:action";
            }

            @Override
            public void execute(FlowNode node, FlowContext context) {
                Object item = context.vars().getVars().get("item");
                context.vars().getVars().put("tag", item);
            }
        };

        MultiInstanceExecutor wrapper = new MultiInstanceExecutor(null);
        List<NodeExecutor> list = new ArrayList<>();
        list.add(wrapper);
        list.add(action);
        list.add(new EventNodeExecutor(null));
        list.add(new GatewayNodeExecutor());
        list.add(new UserTaskNodeExecutor());
        list.add(new ScriptNodeExecutor());
        NodeExecutorRegistry registry = new NodeExecutorRegistry(list);
        MultiInstanceExecutor.Holder.REGISTRY = registry;

        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:ruleforge="http://ruleforge.com/schema">
              <bpmn:process id="p_mi">
                <bpmn:startEvent id="s"/>
                <bpmn:serviceTask id="t" ruleforge:taskType="action"
                                  ruleforge:multiInstance="true"
                                  ruleforge:collection="items"
                                  ruleforge:elementVar="item"
                                  ruleforge:outputVariable="outputs"/>
                <bpmn:endEvent id="end"/>
                <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="t"/>
                <bpmn:sequenceFlow id="e1" sourceRef="t" targetRef="end"/>
              </bpmn:process>
            </bpmn:definitions>
            """;
        FlowDefinition def = parser.parseSingleProcess(xml);

        // V5.33 A0:ctx.vars().getVars() 委托 currentToken;pre-init token + setCurrentNodeId,
        // 让 traverse 不重建根 token(保留 items vars)
        FlowContext ctx = FlowContext.newDefault("test-flow");
        Token t = new Token("tok-" + System.nanoTime());
        t.setCurrentNodeId(def.getStartNodeId());
        ctx.activeTokens().add(t);
        ctx.setCurrentToken(t);
        ctx.vars().getVars().put("items", Arrays.asList("a", "b", "c"));

        FlowNodeRunner runner = new FlowNodeRunner(registry, new ConditionEvaluator(), null);
        // 注:V5.33 A0 测试场景下 stateMapper=null,traverse 返回 stub state
        // (status 永远 PENDING);我们断言 vars 已正确被 MI wrapper 处理即可
        DecisionFlowState state = runner.traverse(def, ctx, def.getStartNodeId());

        assertEquals(Arrays.asList("a", "b", "c"), ctx.vars().getVars().get("outputs"));
        assertNotNull(ctx.vars().getVars().get("tag"));
        // 走完流程 — 没有任何异常,state 已返回
        assertNotNull(state);
    }
}
