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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.33 A1 — MultiInstanceExecutor 行为规范。
 *
 * <p>Mirror Rust V5.29 7 个集成测试(`experiments/server-rust/crates/rf-executor/tests/multi_instance_test.rs`),
 * 1:1 行为对齐。
 */
@DisplayName("MultiInstanceExecutor 行为")
class MultiInstanceExecutorTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    /**
     * Stub inner ActionNodeExecutor — 按 ruleforge:method 路由。
     */
    static class StubActionExecutor implements NodeExecutor {
        final Map<String, java.util.function.BiConsumer<FlowNode, FlowContext>> handlers = new java.util.HashMap<>();

        StubActionExecutor register(String method,
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
                throw new IllegalStateException("No handler for method=" + m
                    + " at node " + node.getNodeId());
            }
            h.accept(node, context);
        }
    }

    private NodeExecutorRegistry newRegistry(NodeExecutor innerAction) {
        MultiInstanceExecutor wrapper = new MultiInstanceExecutor();
        List<NodeExecutor> executors = new ArrayList<>();
        executors.add(wrapper);
        executors.add(innerAction);
        executors.add(new EventNodeExecutor());
        executors.add(new GatewayNodeExecutor());
        executors.add(new UserTaskNodeExecutor());
        executors.add(new ScriptNodeExecutor());
        NodeExecutorRegistry reg = new NodeExecutorRegistry(executors);
        MultiInstanceExecutor.Holder.REGISTRY = reg;
        return reg;
    }

    /** V5.33 A0:ctx.getVars() 委托 currentToken;init token 让 put 走 token.vars(非 ctx.vars)。
     * 注:不在这里 setCurrentNodeId,因为单元测试是直接调 wrapper,不走 Runner traverse。 */
    private FlowContext newCtx() {
        FlowContext ctx = new FlowContext();
        ctx.setFlowRunId("test-" + System.nanoTime());
        Token t = new Token("tok-" + System.nanoTime());
        ctx.getActiveTokens().add(t);
        ctx.setCurrentToken(t);
        return ctx;
    }

    /** 标准 fixture 模板:start → MI task → end。 */
    private static final String MI_FIXTURE_PREFIX = """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:ruleforge="http://ruleforge.com/schema">
          <bpmn:process id="p1">
            <bpmn:startEvent id="s"/>
        """;

    private static final String MI_FIXTURE_SUFFIX = """
            <bpmn:endEvent id="e"/>
            <bpmn:sequenceFlow id="f0" sourceRef="s" targetRef="t"/>
            <bpmn:sequenceFlow id="f1" sourceRef="t" targetRef="e"/>
          </bpmn:process>
        </bpmn:definitions>
        """;

    /** 拼装: prefix + task node + suffix */
    private String miXml(String taskNode) {
        return MI_FIXTURE_PREFIX + taskNode + MI_FIXTURE_SUFFIX;
    }

    @Nested
    @DisplayName("Parallel MI")
    class Parallel {

        @Test
        @DisplayName("Given items=[a,b,c] parallel + tag_with_item,When 跑完,Then tag ∈ {a,b,c},outputs=[a,b,c]")
        void parallel_runs_all_items_to_completion() throws Exception {
            StubActionExecutor action = new StubActionExecutor()
                .register("tag_with_item", (n, c) -> {
                    Object item = c.getVars().get("item");
                    c.getVars().put("tag", item);
                });
            NodeExecutorRegistry registry = newRegistry(action);

            String taskNode = """
                    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                                      ruleforge:method="tag_with_item"
                                      ruleforge:multiInstance="true"
                                      ruleforge:collection="items"
                                      ruleforge:elementVar="item"
                                      ruleforge:outputVariable="outputs"/>
                """;
            FlowDefinition def = parser.parseSingleProcess(miXml(taskNode));
            FlowContext ctx = newCtx();
            ctx.getVars().put("items", Arrays.asList("a", "b", "c"));

            NodeExecutor exec = registry.resolve(def.getNode("t"));
            exec.execute(def.getNode("t"), ctx);

            Object tag = ctx.getVars().get("tag");
            assertTrue("a".equals(tag) || "b".equals(tag) || "c".equals(tag),
                "tag should be one of {a,b,c}, got: " + tag);
            assertEquals(Arrays.asList("a", "b", "c"), ctx.getVars().get("outputs"));
        }

        @Test
        @DisplayName("Given MI task → post → end,When resolve MI + post,Then tag 末班胜出 ∈ {x,y}")
        void parallel_runs_to_post_node() throws Exception {
            StubActionExecutor action = new StubActionExecutor()
                .register("tag_with_item", (n, c) -> c.getVars().put("tag", c.getVars().get("item")));
            NodeExecutorRegistry registry = newRegistry(action);

            String taskNode = """
                    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                                      ruleforge:method="tag_with_item"
                                      ruleforge:multiInstance="true"
                                      ruleforge:collection="items"
                                      ruleforge:elementVar="item"/>
                    <bpmn:serviceTask id="post" ruleforge:taskType="action"
                                      ruleforge:method="tag_with_item"/>
                    <bpmn:sequenceFlow id="fp" sourceRef="post" targetRef="e"/>
                """;
            FlowDefinition def = parser.parseSingleProcess(miXml(taskNode));
            FlowContext ctx = newCtx();
            ctx.getVars().put("items", Arrays.asList("x", "y"));

            NodeExecutor miTaskExec = registry.resolve(def.getNode("t"));
            miTaskExec.execute(def.getNode("t"), ctx);
            NodeExecutor postExec = registry.resolve(def.getNode("post"));
            postExec.execute(def.getNode("post"), ctx);

            Object tag = ctx.getVars().get("tag");
            assertTrue("x".equals(tag) || "y".equals(tag),
                "tag should be x or y, got: " + tag);
        }

        @Test
        @DisplayName("Given items=[] parallel,When 跑,Then outputs=[],tag 不创建")
        void parallel_empty_collection_completes_immediately() throws Exception {
            StubActionExecutor action = new StubActionExecutor()
                .register("tag_with_item", (n, c) -> c.getVars().put("tag", c.getVars().get("item")));
            NodeExecutorRegistry registry = newRegistry(action);

            String taskNode = """
                    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                                      ruleforge:method="tag_with_item"
                                      ruleforge:multiInstance="true"
                                      ruleforge:collection="items"
                                      ruleforge:elementVar="item"
                                      ruleforge:outputVariable="outputs"/>
                """;
            FlowDefinition def = parser.parseSingleProcess(miXml(taskNode));
            FlowContext ctx = newCtx();
            ctx.getVars().put("items", new ArrayList<>());

            NodeExecutor exec = registry.resolve(def.getNode("t"));
            exec.execute(def.getNode("t"), ctx);

            assertEquals(new ArrayList<>(), ctx.getVars().get("outputs"));
            assertFalse(ctx.getVars().containsKey("tag"));
        }
    }

    @Nested
    @DisplayName("Sequential MI")
    class Sequential {

        @Test
        @DisplayName("Given items=[a,b,c] sequential + append_item,When 跑完,Then collected=[a,b,c] 顺序保留")
        void sequential_runs_items_in_order() throws Exception {
            StubActionExecutor action = new StubActionExecutor()
                .register("append_item", (n, c) -> {
                    Object item = c.getVars().get("item");
                    @SuppressWarnings("unchecked")
                    List<Object> cur = (List<Object>) c.getVars().getOrDefault("collected", new ArrayList<>());
                    List<Object> next = new ArrayList<>(cur);
                    next.add(item);
                    c.getVars().put("collected", next);
                });
            NodeExecutorRegistry registry = newRegistry(action);

            String taskNode = """
                    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                                      ruleforge:method="append_item"
                                      ruleforge:multiInstance="true"
                                      ruleforge:multiInstanceSequential="true"
                                      ruleforge:collection="items"
                                      ruleforge:elementVar="item"/>
                """;
            FlowDefinition def = parser.parseSingleProcess(miXml(taskNode));
            FlowContext ctx = newCtx();
            ctx.getVars().put("items", Arrays.asList("a", "b", "c"));

            NodeExecutor exec = registry.resolve(def.getNode("t"));
            exec.execute(def.getNode("t"), ctx);

            assertEquals(Arrays.asList("a", "b", "c"), ctx.getVars().get("collected"));
        }

        @Test
        @DisplayName("Given items=[] sequential + collect_count,When 跑,Then count 不创建(inner 没跑)")
        void sequential_empty_collection_completes_immediately() throws Exception {
            StubActionExecutor action = new StubActionExecutor()
                .register("collect_count", (n, c) -> {
                    Integer cur = (Integer) c.getVars().getOrDefault("count", 0);
                    c.getVars().put("count", cur + 1);
                });
            NodeExecutorRegistry registry = newRegistry(action);

            String taskNode = """
                    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                                      ruleforge:method="collect_count"
                                      ruleforge:multiInstance="true"
                                      ruleforge:multiInstanceSequential="true"
                                      ruleforge:collection="items"
                                      ruleforge:elementVar="item"/>
                """;
            FlowDefinition def = parser.parseSingleProcess(miXml(taskNode));
            FlowContext ctx = newCtx();
            ctx.getVars().put("items", new ArrayList<>());

            NodeExecutor exec = registry.resolve(def.getNode("t"));
            exec.execute(def.getNode("t"), ctx);

            assertFalse(ctx.getVars().containsKey("count"));
        }
    }

    @Nested
    @DisplayName("Pass-through / 错误路径")
    class Misc {

        @Test
        @DisplayName("Given 无 multiInstance attr,When resolve,Then 走 inner,不入 wrapper")
        void no_multi_instance_attr_passes_through_to_inner() throws Exception {
            StubActionExecutor action = new StubActionExecutor()
                .register("tag_with_item", (n, c) -> {
                    // pass-through 时 elementVar 不应被 wrapper 写入
                    c.getVars().put("tag", c.getVars().get("item"));
                });
            NodeExecutorRegistry registry = newRegistry(action);

            String taskNode = """
                    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                                      ruleforge:method="tag_with_item"/>
                """;
            FlowDefinition def = parser.parseSingleProcess(miXml(taskNode));
            FlowContext ctx = newCtx();
            ctx.getVars().put("items", Arrays.asList("a", "b", "c"));

            NodeExecutor exec = registry.resolve(def.getNode("t"));
            exec.execute(def.getNode("t"), ctx);

            // resolve 路由到 inner action(没 multiInstance attr)
            assertTrue(exec instanceof StubActionExecutor,
                "Expected inner action executor, got: " + exec.getClass().getSimpleName());
            // tag = null(wrapper 没介入,vars 里没 item)
            assertNull(ctx.getVars().get("tag"));
            // outputs 不应存在
            assertFalse(ctx.getVars().containsKey("outputs"));
        }

        @Test
        @DisplayName("Given collection 变量缺失,When 跑 MI,Then 抛 FlowExecutionException,msg 含 'array'/'collection'")
        void missing_collection_var_errors() throws Exception {
            StubActionExecutor action = new StubActionExecutor()
                .register("tag_with_item", (n, c) -> { /* noop */ });
            NodeExecutorRegistry registry = newRegistry(action);

            String taskNode = """
                    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                                      ruleforge:method="tag_with_item"
                                      ruleforge:multiInstance="true"
                                      ruleforge:collection="items"
                                      ruleforge:elementVar="item"
                                      ruleforge:outputVariable="outputs"/>
                """;
            FlowDefinition def = parser.parseSingleProcess(miXml(taskNode));
            FlowContext ctx = newCtx();
            ctx.getVars().put("items", null);

            NodeExecutor exec = registry.resolve(def.getNode("t"));
            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> exec.execute(def.getNode("t"), ctx));
            String msg = ex.getMessage();
            assertTrue(msg.contains("array") || msg.contains("collection"),
                "error msg should mention 'array' or 'collection', got: " + msg);
        }

        @Test
        @DisplayName("Given collection 是 String 不是 List,When 跑 MI,Then 抛异常,msg 含 'array'/'string'")
        void collection_not_an_array_errors() throws Exception {
            StubActionExecutor action = new StubActionExecutor()
                .register("tag_with_item", (n, c) -> { /* noop */ });
            NodeExecutorRegistry registry = newRegistry(action);

            String taskNode = """
                    <bpmn:serviceTask id="t" ruleforge:taskType="action"
                                      ruleforge:method="tag_with_item"
                                      ruleforge:multiInstance="true"
                                      ruleforge:collection="items"
                                      ruleforge:elementVar="item"
                                      ruleforge:outputVariable="outputs"/>
                """;
            FlowDefinition def = parser.parseSingleProcess(miXml(taskNode));
            FlowContext ctx = newCtx();
            ctx.getVars().put("items", "not-a-list");

            NodeExecutor exec = registry.resolve(def.getNode("t"));
            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> exec.execute(def.getNode("t"), ctx));
            String msg = ex.getMessage();
            assertTrue(msg.contains("array") || msg.contains("string"),
                "error msg should mention 'array' or 'string', got: " + msg);
        }
    }
}
