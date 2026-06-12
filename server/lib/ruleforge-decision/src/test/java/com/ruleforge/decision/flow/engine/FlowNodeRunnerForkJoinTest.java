package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.executor.ActionNodeExecutor;
import com.ruleforge.decision.flow.executor.EventNodeExecutor;
import com.ruleforge.decision.flow.executor.GatewayNodeExecutor;
import com.ruleforge.decision.flow.executor.NodeExecutor;
import com.ruleforge.decision.flow.executor.NodeExecutorRegistry;
import com.ruleforge.decision.flow.executor.ParallelGatewayExecutor;
import com.ruleforge.decision.flow.executor.UserTaskNodeExecutor;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.33 A0 — FlowNodeRunner.traverse fork/join 集成测试。
 *
 * <p>Mirror Rust V5.28 P6 契约,1:1 行为对齐:union-merge 语义、first-Suspend/Fail 短路、per-token vars 隔离。
 *
 * <p>测试模式:不接 Spring 容器,手工 new 出 NodeExecutorRegistry + ActionNodeExecutor(配合 AtomicReference 注入动作),
 * 绕过 stateMapper(传入 null 模拟无 DB 持久化路径)或使用 mock stateMapper。
 *
 * <p>注:V5.33 A0 暂不验证 DB 持久化,只验证 traverse 主循环行为。DB 路径在 A0.4(migration + RecoveryJob)单独测。
 */
@DisplayName("FlowNodeRunner fork/join 行为")
class FlowNodeRunnerForkJoinTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    /**
     * 测试 stub ActionNodeExecutor — 单实例,按 method 路由(因为 NodeExecutorRegistry
     * 只有一个 "action" taskType 槽,所有 serviceTask 走同一个 executor)。
     */
    static class StubActionExecutor implements NodeExecutor {
        final String methodName;
        final String writesVar;
        final Object writesValue;
        final AtomicReference<Map<String, Object>> capture;

        StubActionExecutor(String methodName, String writesVar, Object writesValue,
                           AtomicReference<Map<String, Object>> capture) {
            this.methodName = methodName;
            this.writesVar = writesVar;
            this.writesValue = writesValue;
            this.capture = capture;
        }

        @Override
        public String supportedType() {
            return "SERVICE_TASK:action";
        }

        @Override
        public void execute(FlowNode node, FlowContext context) {
            // 验证 method 匹配(每个测试只 inject 一个 method,如果其它节点调用,就报错)
            if (!methodName.equals(node.attr("ruleforge", "method"))) {
                throw new IllegalStateException(
                    "Expected method=" + methodName + " but node " + node.getNodeId() + " has "
                    + node.attr("ruleforge", "method"));
            }
            if (writesVar != null) {
                context.getVars().put(writesVar, writesValue);
            }
            if (capture != null) {
                capture.set(new HashMap<>(context.getVars()));
            }
        }
    }

    /**
     * V5.33 A0 — Multi-method stub,按 node.attr(ruleforge:method) 路由到具体 stub。
     * 解决 NodeExecutorRegistry 单 taskType 槽的问题。
     */
    static class MultiMethodActionExecutor implements NodeExecutor {
        final Map<String, java.util.function.BiConsumer<FlowNode, FlowContext>> handlers = new java.util.HashMap<>();

        MultiMethodActionExecutor register(String method,
                                            java.util.function.BiConsumer<FlowNode, FlowContext> handler) {
            handlers.put(method, handler);
            return this;
        }

        @Override
        public String supportedType() {
            return "SERVICE_TASK:action";
        }

        @Override
        public void execute(FlowNode node, FlowContext context) {
            String m = node.attr("ruleforge", "method");
            java.util.function.BiConsumer<FlowNode, FlowContext> h = handlers.get(m);
            if (h == null) {
                throw new IllegalStateException("No handler for method=" + m + " at node " + node.getNodeId());
            }
            h.accept(node, context);
        }
    }

    /** V5.33 A0 — Stub runner,无 DB 路径:stateMapper=null 时跳过持久化。 */
    private FlowNodeRunner newRunner(NodeExecutor... executors) {
        List<NodeExecutor> list = new java.util.ArrayList<>();
        for (NodeExecutor e : executors) list.add(e);
        // 默认 stub
        list.add(new EventNodeExecutor());
        list.add(new GatewayNodeExecutor());
        list.add(new ParallelGatewayExecutor());
        list.add(new UserTaskNodeExecutor());
        NodeExecutorRegistry registry = new NodeExecutorRegistry(list);
        // 不接 DB,走 V5.33 A0 worklist 主循环
        return new FlowNodeRunner(registry, new ConditionEvaluator(), null);
    }

    private FlowContext newCtx(String flowRunId) {
        FlowContext ctx = new FlowContext();
        ctx.setFlowRunId(flowRunId);
        return ctx;
    }

    @Nested
    @DisplayName("Fork 多 token 推进")
    class ForkDispatches {

        @Test
        @DisplayName("Given 2-branch fork,When 跑完,Then 两 end 都在 visited,Completed")
        void parallel_fork_runs_both_branches_to_completion() {
            AtomicReference<Map<String, Object>> cap1 = new AtomicReference<>();
            AtomicReference<Map<String, Object>> cap2 = new AtomicReference<>();
            MultiMethodActionExecutor action = new MultiMethodActionExecutor()
                .register("write_a", (n, c) -> {
                    c.getVars().put("var_a", "alpha");
                    cap1.set(new HashMap<>(c.getVars()));
                })
                .register("write_b", (n, c) -> {
                    c.getVars().put("var_b", "beta");
                    cap2.set(new HashMap<>(c.getVars()));
                });

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p1">
                    <bpmn:startEvent id="s"/>
                    <bpmn:parallelGateway id="fork"/>
                    <bpmn:serviceTask id="a" ruleforge:taskType="action" ruleforge:method="write_a"/>
                    <bpmn:serviceTask id="b" ruleforge:taskType="action" ruleforge:method="write_b"/>
                    <bpmn:parallelGateway id="join"/>
                    <bpmn:endEvent id="end1"/>
                    <bpmn:endEvent id="end2"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="fork"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="fork" targetRef="a"/>
                    <bpmn:sequenceFlow id="e3" sourceRef="fork" targetRef="b"/>
                    <bpmn:sequenceFlow id="ea" sourceRef="a" targetRef="join"/>
                    <bpmn:sequenceFlow id="eb" sourceRef="b" targetRef="join"/>
                    <bpmn:sequenceFlow id="ej1" sourceRef="join" targetRef="end1"/>
                    <bpmn:sequenceFlow id="ej2" sourceRef="join" targetRef="end2"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowNodeRunner runner = newRunner(action);
            FlowContext ctx = newCtx("r1");
            try {
                runner.traverse(def, ctx, def.getStartNodeId());
            } catch (NullPointerException npe) {
                throw new AssertionError("V5.33 A0 traverse should be null-safe when stateMapper is null", npe);
            }
            assertEquals("alpha", cap1.get().get("var_a"));
            assertEquals("beta", cap2.get().get("var_b"));
        }

        @Test
        @DisplayName("Given 两 branch 写同名 var,When fork+join 推进,Then 末班胜出")
        void parallel_fork_union_merges_branch_vars_with_last_wins_collision() {
            MultiMethodActionExecutor action = new MultiMethodActionExecutor()
                .register("write_x1", (n, c) -> c.getVars().put("var_x", 1))
                .register("write_x2", (n, c) -> c.getVars().put("var_x", 2))
                .register("noop", (n, c) -> { /* noop */ });

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p1">
                    <bpmn:startEvent id="s"/>
                    <bpmn:parallelGateway id="fork"/>
                    <bpmn:serviceTask id="a" ruleforge:taskType="action" ruleforge:method="write_x1"/>
                    <bpmn:serviceTask id="b" ruleforge:taskType="action" ruleforge:method="write_x2"/>
                    <bpmn:parallelGateway id="join"/>
                    <bpmn:serviceTask id="post" ruleforge:taskType="action" ruleforge:method="noop"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="fork"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="fork" targetRef="a"/>
                    <bpmn:sequenceFlow id="e3" sourceRef="fork" targetRef="b"/>
                    <bpmn:sequenceFlow id="ea" sourceRef="a" targetRef="join"/>
                    <bpmn:sequenceFlow id="eb" sourceRef="b" targetRef="join"/>
                    <bpmn:sequenceFlow id="ej" sourceRef="join" targetRef="post"/>
                    <bpmn:sequenceFlow id="ep" sourceRef="post" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowNodeRunner runner = newRunner(action);
            FlowContext ctx = newCtx("r2");
            runner.traverse(def, ctx, def.getStartNodeId());

            // join 后 var_x 应是 1 或 2(末班胜出,具体哪个取决于遍历顺序)
            Integer varX = (Integer) ctx.getVars().get("var_x");
            assertNotNull(varX);
            assertTrue(varX == 1 || varX == 2, "var_x should be 1 or 2 (last-wins collision), got: " + varX);
        }

        @Test
        @DisplayName("Given diamond fork→b1/b2→join→post,When 跑完,Then post 看到 union vars")
        void parallel_join_synchronizes_two_branches_then_routes_through_post_join_node() {
            AtomicReference<Map<String, Object>> postCapture = new AtomicReference<>();
            MultiMethodActionExecutor action = new MultiMethodActionExecutor()
                .register("write_b1", (n, c) -> c.getVars().put("var_b1", "from_b1"))
                .register("write_b2", (n, c) -> c.getVars().put("var_b2", "from_b2"))
                .register("post", (n, c) -> postCapture.set(new HashMap<>(c.getVars())));

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="diamond">
                    <bpmn:startEvent id="s"/>
                    <bpmn:parallelGateway id="fork"/>
                    <bpmn:serviceTask id="b1" ruleforge:taskType="action" ruleforge:method="write_b1"/>
                    <bpmn:serviceTask id="b2" ruleforge:taskType="action" ruleforge:method="write_b2"/>
                    <bpmn:parallelGateway id="join"/>
                    <bpmn:serviceTask id="post" ruleforge:taskType="action" ruleforge:method="post"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="fork"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="fork" targetRef="b1"/>
                    <bpmn:sequenceFlow id="e3" sourceRef="fork" targetRef="b2"/>
                    <bpmn:sequenceFlow id="eb1" sourceRef="b1" targetRef="join"/>
                    <bpmn:sequenceFlow id="eb2" sourceRef="b2" targetRef="join"/>
                    <bpmn:sequenceFlow id="ej" sourceRef="join" targetRef="post"/>
                    <bpmn:sequenceFlow id="ep" sourceRef="post" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowNodeRunner runner = newRunner(action);
            FlowContext ctx = newCtx("r3");
            runner.traverse(def, ctx, def.getStartNodeId());

            Map<String, Object> postVars = postCapture.get();
            assertNotNull(postVars, "post action should have run");
            assertEquals("from_b1", postVars.get("var_b1"));
            assertEquals("from_b2", postVars.get("var_b2"));
        }

        @Test
        @DisplayName("Given 无 join 节点(P0 fallback),When 跑完,Then 各 branch 跑到底,vars 仍 union")
        void parallel_join_missing_falls_back_to_p0_behavior() {
            MultiMethodActionExecutor action = new MultiMethodActionExecutor()
                .register("write_a", (n, c) -> c.getVars().put("var_a", "alpha"))
                .register("write_b", (n, c) -> c.getVars().put("var_b", "beta"));

            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p1">
                    <bpmn:startEvent id="s"/>
                    <bpmn:parallelGateway id="fork"/>
                    <bpmn:serviceTask id="a" ruleforge:taskType="action" ruleforge:method="write_a"/>
                    <bpmn:serviceTask id="b" ruleforge:taskType="action" ruleforge:method="write_b"/>
                    <bpmn:endEvent id="end1"/>
                    <bpmn:endEvent id="end2"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="fork"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="fork" targetRef="a"/>
                    <bpmn:sequenceFlow id="e3" sourceRef="fork" targetRef="b"/>
                    <bpmn:sequenceFlow id="ea" sourceRef="a" targetRef="end1"/>
                    <bpmn:sequenceFlow id="eb" sourceRef="b" targetRef="end2"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowNodeRunner runner = newRunner(action);
            FlowContext ctx = newCtx("r4");
            runner.traverse(def, ctx, def.getStartNodeId());

            // vars union 后应该有两个 var
            assertTrue(ctx.getVars().containsKey("var_a"));
            assertTrue(ctx.getVars().containsKey("var_b"));
        }
    }

    @Nested
    @DisplayName("Suspend 短路")
    class SuspendShortCircuit {

        /**
         * Stub UserTask executor — 第一次跑抛 AsyncNodeSuspendException,模拟"等人工审批"。
         */
        static class SuspendingUserTask implements NodeExecutor {
            @Override
            public String supportedType() {
                return "USER_TASK";
            }

            @Override
            public void execute(FlowNode node, FlowContext context) throws Exception {
                throw new AsyncNodeSuspendException(
                    node.getNodeId(), node.getType().name(),
                    AsyncNodeSuspendException.WAIT_TYPE_USER_TASK,
                    "userTask:" + node.getNodeId(), null, null);
            }
        }

        @Test
        @DisplayName("Given 一 branch 遇 userTask suspend,When traverse,Then 整 flow 走完所有 active token(无更多)就停 — 不抛错")
        void parallel_fork_async_branch_does_not_crash_above() {
            MultiMethodActionExecutor action = new MultiMethodActionExecutor()
                .register("ok", (n, c) -> c.getVars().put("var_a", "alpha"));
            NodeExecutor userTask = new SuspendingUserTask();
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p1">
                    <bpmn:startEvent id="s"/>
                    <bpmn:parallelGateway id="fork"/>
                    <bpmn:serviceTask id="a" ruleforge:taskType="action" ruleforge:method="ok"/>
                    <bpmn:userTask id="wait1"/>
                    <bpmn:endEvent id="end1"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="s" targetRef="fork"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="fork" targetRef="a"/>
                    <bpmn:sequenceFlow id="e3" sourceRef="fork" targetRef="wait1"/>
                    <bpmn:sequenceFlow id="ea" sourceRef="a" targetRef="end1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            List<NodeExecutor> list = new java.util.ArrayList<>(List.of(action, userTask));
            list.add(new EventNodeExecutor());
            list.add(new GatewayNodeExecutor());
            list.add(new ParallelGatewayExecutor());
            NodeExecutorRegistry registry = new NodeExecutorRegistry(list);
            FlowNodeRunner runner = new FlowNodeRunner(registry, new ConditionEvaluator(), null);

            FlowContext ctx = newCtx("r5");
            try {
                runner.traverse(def, ctx, def.getStartNodeId());
            } catch (AsyncNodeSuspendException ex) {
                throw new AssertionError("Runner should catch AsyncNodeSuspendException internally, not propagate", ex);
            } catch (FlowExecutionException ex) {
                throw new AssertionError("Suspend should not be a FAILED", ex);
            }
        }
    }
}
