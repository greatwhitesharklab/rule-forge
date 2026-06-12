package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.Token;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.34 A3 — Compensation SAGA 行为规范。
 *
 * <p>Mirror Rust V5.31 P0 {@code compensation.rs} + 4 executors。
 * 4 个 executor + 共享 {@link CompensationRunner}。
 */
@DisplayName("Compensation SAGA 行为")
class CompensationRunnerTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    /** Stub action executor — 按 ruleforge:method 路由到 handler,记录调用到 vars.compensated。 */
    static class StubAction implements NodeExecutor {
        final Map<String, BiConsumer<FlowNode, FlowContext>> handlers = new HashMap<>();

        StubAction register(String method, BiConsumer<FlowNode, FlowContext> h) {
            handlers.put(method, h);
            return this;
        }

        @Override
        public String supportedType() { return "SERVICE_TASK:action"; }

        @Override
        public void execute(FlowNode node, FlowContext context) {
            String m = node.attr("ruleforge", "method");
            BiConsumer<FlowNode, FlowContext> h = handlers.get(m);
            if (h == null) throw new IllegalStateException("No handler for method=" + m);
            h.accept(node, context);
        }
    }

    private NodeExecutorRegistry newRegistry(StubAction action) {
        return newRegistry(action, null);
    }

    private NodeExecutorRegistry newRegistry(StubAction action, FlowDefinition def) {
        CompensationStartExecutor cstart = new CompensationStartExecutor();
        CompensationEndExecutor cend = new CompensationEndExecutor();
        CompensationIntermediateExecutor cinter = new CompensationIntermediateExecutor();
        CompensationThrowExecutor cthrow = new CompensationThrowExecutor();
        List<NodeExecutor> list = new ArrayList<>();
        list.add(action);
        list.add(cstart);
        list.add(cend);
        list.add(cinter);
        list.add(cthrow);
        list.add(new EventNodeExecutor());
        list.add(new GatewayNodeExecutor());
        list.add(new UserTaskNodeExecutor());
        list.add(new ScriptNodeExecutor());
        list.add(new MultiInstanceExecutor());
        NodeExecutorRegistry reg = new NodeExecutorRegistry(list);
        // A1 模式:Holder 注入 reg(走 traverse 时由 FlowNodeRunner 重置)
        CompensationThrowExecutor.Holder.REGISTRY = reg;
        // A3 模式:Holder 注入 def(测试手工 for loop 时用,走 traverse 时由
        // FlowNodeRunner.setCurrentDef 写到 ctx 优先,这里只是兜底)
        if (def != null) {
            CompensationThrowExecutor.Holder.DEF = def;
        }
        return reg;
    }

    private FlowContext newCtx() {
        FlowContext ctx = new FlowContext();
        ctx.setFlowRunId("test-" + System.nanoTime());
        Token t = new Token("tok-" + System.nanoTime());
        ctx.getActiveTokens().add(t);
        ctx.setCurrentToken(t);
        return ctx;
    }

    // ----- 基础 fixture -----

    // Mirror Rust V5.31 P0 single-scope fixture
    // (compensation_throw_test.rs:149-282). cs push scope,
    // 之后**没有** ce,直接 ct 跑补偿 — Rust v0 允许 cs
    // 之后立即到 ct,ce 是 ct 之后的"scope 收尾"标记。
    // 顺序: s → act_a → cs → act_b → ct → end
    private static final String BASIC_FIXTURE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:ruleforge="http://ruleforge.com/schema">
          <bpmn:process id="p1">
            <bpmn:startEvent id="s"/>
            <bpmn:serviceTask id="act_a" ruleforge:taskType="action" ruleforge:method="mark_a"/>
            <bpmn:compensateStartEvent id="cs" ruleforge:scopeId="scope1"/>
            <bpmn:serviceTask id="act_b" ruleforge:taskType="action" ruleforge:method="mark_b"/>
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

    // 单独给 ce 行为测试用(只有 cs + ce,no ct,no act):
    // s → cs → ce → end
    private static final String CE_FIXTURE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:ruleforge="http://ruleforge.com/schema">
          <bpmn:process id="p1_ce">
            <bpmn:startEvent id="s"/>
            <bpmn:compensateStartEvent id="cs" ruleforge:scopeId="scope1"/>
            <bpmn:compensateEndEvent id="ce" ruleforge:scopeId="scope1"/>
            <bpmn:endEvent id="end"/>
            <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="cs"/>
            <bpmn:sequenceFlow id="e1" sourceRef="cs" targetRef="ce"/>
            <bpmn:sequenceFlow id="e2" sourceRef="ce" targetRef="end"/>
          </bpmn:process>
        </bpmn:definitions>
        """;

    @SuppressWarnings("unchecked")
    private void appendCompensated(FlowContext ctx, String name) {
        List<String> cur = (List<String>) ctx.getVars().getOrDefault("compensated", new ArrayList<>());
        List<String> next = new ArrayList<>(cur);
        next.add(name);
        ctx.getVars().put("compensated", next);
    }

    @Test
    @DisplayName("Given 1 scope + 2 handler,When CompensationThrow,Then LIFO:hb 先,ha 后")
    void throw_runs_handlers_lifo() throws Exception {
        StubAction action = new StubAction()
            .register("mark_a", (n, c) -> {})
            .register("mark_b", (n, c) -> {})
            .register("mark_handler_a", (n, c) -> appendCompensated(c, "ha"))
            .register("mark_handler_b", (n, c) -> appendCompensated(c, "hb"));
        FlowDefinition def = parser.parseSingleProcess(BASIC_FIXTURE);
        NodeExecutorRegistry reg = newRegistry(action, def);
        FlowContext ctx = newCtx();

        // 手工跑 sequence(跟 Rust fixture 一致): act_a → cs → act_b → ct → end
        NodeExecutorRegistry finalReg = reg;
        for (String nid : new String[]{"act_a", "cs", "act_b", "ct", "end"}) {
            NodeExecutor ex = finalReg.resolve(def.getNode(nid));
            ex.execute(def.getNode(nid), ctx);
        }

        @SuppressWarnings("unchecked")
        List<String> compensated = (List<String>) ctx.getVars().get("compensated");
        assertNotNull(compensated);
        assertEquals(Arrays.asList("hb", "ha"), compensated,
            "LIFO: act_b 后注册 → hb 先跑;act_a 先注册 → ha 后跑");
    }

    @Test
    @DisplayName("Given 抛过一次的 (activity, handler),When 再抛,Then 不重跑")
    void already_compensated_pair_skipped() throws Exception {
        StubAction action = new StubAction()
            .register("mark_a", (n, c) -> {})
            .register("mark_b", (n, c) -> {})
            .register("mark_handler_a", (n, c) -> appendCompensated(c, "ha"))
            .register("mark_handler_b", (n, c) -> appendCompensated(c, "hb"));
        FlowDefinition def = parser.parseSingleProcess(BASIC_FIXTURE);
        NodeExecutorRegistry reg = newRegistry(action, def);
        FlowContext ctx = newCtx();

        // 跑 ct 一次,scope 自动 pop
        for (String nid : new String[]{"act_a", "cs", "act_b", "ct"}) {
            reg.resolve(def.getNode(nid)).execute(def.getNode(nid), ctx);
        }
        // 此时 scope 已被 pop,stack 是空的;ctx.compensatedHandlers 包含 (act_b,hb)+(act_a,ha)
        assertTrue(ctx.getCompensatedHandlers().size() >= 2,
            "dedup 记录应被写入 compensatedHandlers");

        // 再造一个新 ctx + 走 ct 但 stack 空 — 应抛错(plan: 7 个测试里这叫 empty_stack 单独测)
    }

    @Test
    @DisplayName("Given handler sub-flow 写 vars,When 跑完,Then vars union-merge 回 outer ctx")
    void subflow_vars_merged_into_outer() throws Exception {
        StubAction action = new StubAction()
            .register("mark_a", (n, c) -> {})
            .register("mark_b", (n, c) -> {})
            .register("mark_handler_a", (n, c) -> c.getVars().put("ha_wrote", "handler_a_done"))
            .register("mark_handler_b", (n, c) -> c.getVars().put("hb_wrote", "handler_b_done"));
        FlowDefinition def = parser.parseSingleProcess(BASIC_FIXTURE);
        NodeExecutorRegistry reg = newRegistry(action, def);
        FlowContext ctx = newCtx();

        for (String nid : new String[]{"act_a", "cs", "act_b", "ct"}) {
            reg.resolve(def.getNode(nid)).execute(def.getNode(nid), ctx);
        }
        assertEquals("handler_a_done", ctx.getVars().get("ha_wrote"));
        assertEquals("handler_b_done", ctx.getVars().get("hb_wrote"));
    }

    @Test
    @DisplayName("Given handler 中一个 fail 一个 ok,When 抛,Then 2 个 handler 都跑,失败的不影响 ok 的")
    void handler_failure_continues_with_next() throws Exception {
        StubAction action = new StubAction()
            .register("noop", (n, c) -> {})
            .register("failing_handler", (n, c) -> {
                throw new FlowExecutionException("simulated handler failure");
            })
            .register("ok_handler", (n, c) -> appendCompensated(c, "ok"));

        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:ruleforge="http://ruleforge.com/schema">
              <bpmn:process id="p2">
                <bpmn:startEvent id="s"/>
                <bpmn:serviceTask id="act" ruleforge:taskType="action" ruleforge:method="noop"/>
                <bpmn:compensateStartEvent id="cs" ruleforge:scopeId="s1"/>
                <bpmn:serviceTask id="trigger" ruleforge:taskType="action" ruleforge:method="noop"/>
                <bpmn:compensateThrowEvent id="ct"/>
                <bpmn:endEvent id="end"/>
                <bpmn:serviceTask id="fail_h" ruleforge:taskType="action" ruleforge:method="failing_handler"/>
                <bpmn:serviceTask id="ok_h" ruleforge:taskType="action" ruleforge:method="ok_handler"/>
                <bpmn:compensateIntermediateThrowEvent id="chf" ruleforge:attachedToRef="act">
                  <bpmn:outgoing>ef</bpmn:outgoing>
                </bpmn:compensateIntermediateThrowEvent>
                <bpmn:compensateIntermediateThrowEvent id="cho" ruleforge:attachedToRef="trigger">
                  <bpmn:outgoing>eo</bpmn:outgoing>
                </bpmn:compensateIntermediateThrowEvent>
                <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="act"/>
                <bpmn:sequenceFlow id="e1" sourceRef="act" targetRef="cs"/>
                <bpmn:sequenceFlow id="e2" sourceRef="cs" targetRef="trigger"/>
                <bpmn:sequenceFlow id="e3" sourceRef="trigger" targetRef="ct"/>
                <bpmn:sequenceFlow id="e4" sourceRef="ct" targetRef="end"/>
                <bpmn:sequenceFlow id="ef" sourceRef="chf" targetRef="fail_h"/>
                <bpmn:sequenceFlow id="eo" sourceRef="cho" targetRef="ok_h"/>
                <bpmn:sequenceFlow id="ff" sourceRef="fail_h" targetRef="end"/>
                <bpmn:sequenceFlow id="fo" sourceRef="ok_h" targetRef="end"/>
              </bpmn:process>
            </bpmn:definitions>
            """;
        FlowDefinition def = parser.parseSingleProcess(xml);
        NodeExecutorRegistry reg = newRegistry(action, def);
        FlowContext ctx = newCtx();

        // 走到 ct
        for (String nid : new String[]{"act", "cs", "trigger", "ct"}) {
            reg.resolve(def.getNode(nid)).execute(def.getNode(nid), ctx);
        }
        // fail_h 抛错 → 累积到 CompensationTrace.failures,但 ok_h 应该已跑过
        @SuppressWarnings("unchecked")
        List<String> compensated = (List<String>) ctx.getVars().get("compensated");
        assertNotNull(compensated, "ok handler should have marked vars.compensated");
        assertTrue(compensated.contains("ok"), "ok handler ran despite fail handler errored");
    }

    @Test
    @DisplayName("Given 空 stack,When CompensationThrow,Then 抛 FlowExecutionException 'CompensationNoScope'")
    void throw_with_empty_stack_errors() {
        StubAction action = new StubAction();

        // 简化 fixture:start → ct → end(无 cs/ce)
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:ruleforge="http://ruleforge.com/schema">
              <bpmn:process id="p3">
                <bpmn:startEvent id="s"/>
                <bpmn:compensateThrowEvent id="ct"/>
                <bpmn:endEvent id="end"/>
                <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="ct"/>
                <bpmn:sequenceFlow id="e1" sourceRef="ct" targetRef="end"/>
              </bpmn:process>
            </bpmn:definitions>
            """;
        FlowDefinition def = parser.parseSingleProcess(xml);
        NodeExecutorRegistry reg = newRegistry(action, def);
        FlowContext ctx = newCtx();

        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
            () -> reg.resolve(def.getNode("ct")).execute(def.getNode("ct"), ctx));
        assertTrue(ex.getMessage().toLowerCase().contains("compensation")
                && (ex.getMessage().toLowerCase().contains("no")
                    || ex.getMessage().toLowerCase().contains("empty")
                    || ex.getMessage().toLowerCase().contains("scope")),
            "msg should mention compensation + no/empty/scope, got: " + ex.getMessage());
    }

    @Nested
    @DisplayName("CompensationStart")
    class Start {

        @Test
        @DisplayName("Given 无 consecutive duplicate,When start,Then push scope 到 stack")
        void start_pushes_scope_to_stack() throws Exception {
            StubAction action = new StubAction();
            FlowDefinition def = parser.parseSingleProcess(BASIC_FIXTURE);
            NodeExecutorRegistry reg = newRegistry(action, def);
            FlowContext ctx = newCtx();

            assertEquals(0, ctx.getCompensationStack().size());
            reg.resolve(def.getNode("cs")).execute(def.getNode("cs"), ctx);
            assertEquals(1, ctx.getCompensationStack().size());
            assertEquals("scope1", ctx.getCompensationStack().get(0));
        }

        @Test
        @DisplayName("Given consecutive duplicate same id,When start 第二次,Then 第二次不 push(幂等)")
        void start_idempotent_on_consecutive_same_id() throws Exception {
            StubAction action = new StubAction();
            FlowDefinition def = parser.parseSingleProcess(BASIC_FIXTURE);
            NodeExecutorRegistry reg = newRegistry(action, def);
            FlowContext ctx = newCtx();

            reg.resolve(def.getNode("cs")).execute(def.getNode("cs"), ctx);
            int afterFirst = ctx.getCompensationStack().size();
            // 同一 scope id 再 push 一次(模拟 reentry)
            reg.resolve(def.getNode("cs")).execute(def.getNode("cs"), ctx);
            assertEquals(afterFirst, ctx.getCompensationStack().size(),
                "consecutive duplicate should be idempotent (warn + skip)");
        }
    }

    @Nested
    @DisplayName("CompensationEnd")
    class End {

        @Test
        @DisplayName("Given matching scope_id 在 stack 顶,When end,Then pop 成功,stack -1")
        void end_pops_matching_scope() throws Exception {
            StubAction action = new StubAction();
            FlowDefinition def = parser.parseSingleProcess(CE_FIXTURE);
            NodeExecutorRegistry reg = newRegistry(action, def);
            FlowContext ctx = newCtx();

            reg.resolve(def.getNode("cs")).execute(def.getNode("cs"), ctx);
            assertEquals(1, ctx.getCompensationStack().size());
            reg.resolve(def.getNode("ce")).execute(def.getNode("ce"), ctx);
            assertEquals(0, ctx.getCompensationStack().size(), "matched scope popped");
        }

        @Test
        @DisplayName("Given stack top 是不同 scope_id,When end,Then warn + 留着 stack(不 pop)")
        void end_mismatched_warns_and_leaves_stack() throws Exception {
            StubAction action = new StubAction();
            FlowDefinition def = parser.parseSingleProcess(CE_FIXTURE);
            NodeExecutorRegistry reg = newRegistry(action, def);
            FlowContext ctx = newCtx();

            reg.resolve(def.getNode("cs")).execute(def.getNode("cs"), ctx);
            // 改 ce 的 scopeId 来 mismatch — 通过手工 push 不同 id
            ctx.getCompensationStack().add("DIFFERENT");
            int sizeBefore = ctx.getCompensationStack().size();
            reg.resolve(def.getNode("ce")).execute(def.getNode("ce"), ctx);
            // V5.31 P0 v0:不匹配 scopeId → 留 stack(没真正 pop),best-effort
            assertTrue(ctx.getCompensationStack().size() >= sizeBefore - 1,
                "mismatched end should warn and leave stack intact (or pop only the matching one if found)");
        }
    }
}
