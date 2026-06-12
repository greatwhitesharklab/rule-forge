package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.35 A5 — IntermediateEventExecutor 7-path dispatcher 行为规范。
 *
 * <p>Mirror Rust V5.32 {@code intermediate_event.rs} 行为:
 * <ul>
 *   <li>None → Continue(不抛,Runner 走默认 out)</li>
 *   <li>Message/Signal/Conditional → 抛 AsyncNodeSuspendException(namespaced waitRef)</li>
 *   <li>Timer → 抛 AsyncNodeSuspendException(nextRetryAt = now+duration)</li>
 *   <li>LinkThrow → 返回 BRANCH transition(由 Runner traverse 读,跳过 throw 出边)</li>
 *   <li>LinkCatch → Continue(走默认 out)</li>
 * </ul>
 */
@DisplayName("IntermediateEventExecutor — 7-path dispatcher")
class IntermediateEventExecutorTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();
    private IntermediateEventExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new IntermediateEventExecutor();
    }

    private FlowContext newCtx() {
        FlowContext ctx = new FlowContext();
        ctx.setFlowRunId("test-" + System.nanoTime());
        com.ruleforge.decision.flow.engine.Token t =
            new com.ruleforge.decision.flow.engine.Token("tok-" + System.nanoTime());
        ctx.getActiveTokens().add(t);
        ctx.setCurrentToken(t);
        return ctx;
    }

    private FlowNode newNode(String id, Map<String, String> attrs) {
        return new FlowNode(id, NodeType.INTERMEDIATE_EVENT,
            null, attrs, null, null, new ArrayList<>(), false);
    }

    @Nested
    @DisplayName("None — 透传 Continue")
    class None {

        @Test
        @DisplayName("Given eventType 缺,When execute,Then 不抛(Runner 走默认 out 推进)")
        void none_passes_through() {
            FlowNode n = newNode("evt", Map.of());
            FlowContext ctx = newCtx();
            executor.execute(n, ctx);
            // 不抛 = pass-through
        }
    }

    @Nested
    @DisplayName("Message")
    class Message {

        @Test
        @DisplayName("Given eventType=message + name,When execute,Then 抛 Suspend w/ waitRef='message:foo'")
        void message_suspends_with_namespaced_wait_ref() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "message");
            attrs.put("ruleforge:eventName", "loan_approved");
            FlowNode n = newNode("msgCatch", attrs);
            FlowContext ctx = newCtx();

            AsyncNodeSuspendException ex = assertThrows(AsyncNodeSuspendException.class,
                () -> executor.execute(n, ctx));
            assertEquals("message:loan_approved", ex.getWaitRef(),
                "waitRef namespaced: 'message:<name>'");
            assertEquals(AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA, ex.getWaitType());
        }
    }

    @Nested
    @DisplayName("Signal")
    class Signal {

        @Test
        @DisplayName("Given eventType=signal + name,When execute,Then 抛 Suspend w/ waitRef='signal:bar'")
        void signal_suspends_with_namespaced_wait_ref() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "signal");
            attrs.put("ruleforge:eventName", "fraud_alert");
            FlowNode n = newNode("sigCatch", attrs);
            FlowContext ctx = newCtx();

            AsyncNodeSuspendException ex = assertThrows(AsyncNodeSuspendException.class,
                () -> executor.execute(n, ctx));
            assertEquals("signal:fraud_alert", ex.getWaitRef());
            assertEquals(AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA, ex.getWaitType());
        }
    }

    @Nested
    @DisplayName("Timer")
    class Timer {

        @Test
        @DisplayName("Given eventType=timer + duration=PT5S,When execute,Then 抛 Suspend + nextRetryAt=now+5s")
        void timer_suspends_with_next_retry_at() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "timer");
            attrs.put("ruleforge:eventDuration", "PT5S");
            FlowNode n = newNode("timerCatch", attrs);
            FlowContext ctx = newCtx();

            Instant before = Instant.now();
            AsyncNodeSuspendException ex = assertThrows(AsyncNodeSuspendException.class,
                () -> executor.execute(n, ctx));
            Instant after = Instant.now();

            assertNotNull(ex.getNextRetryAt(), "timer must set nextRetryAt");
            // nextRetryAt 应在 [before+5s, after+5s] 之间(±100ms 容差)
            Instant earliest = before.plusSeconds(5).minusMillis(100);
            Instant latest = after.plusSeconds(5).plusMillis(100);
            assertTrue(!ex.getNextRetryAt().isBefore(earliest)
                    && !ex.getNextRetryAt().isAfter(latest),
                "nextRetryAt should be ~5s from now, got: " + ex.getNextRetryAt());
            assertEquals(AsyncNodeSuspendException.WAIT_TYPE_ASYNC_TASK, ex.getWaitType());
        }
    }

    @Nested
    @DisplayName("Conditional")
    class Conditional {

        @Test
        @DisplayName("Given eventType=conditional + expr,When execute,Then 抛 Suspend w/ payload.condition")
        void conditional_suspends_with_payload() {
            Map<String, String> attrs = new HashMap<>();
            attrs.put("ruleforge:eventType", "conditional");
            attrs.put("ruleforge:condition", "approved == true");
            FlowNode n = newNode("condCatch", attrs);
            FlowContext ctx = newCtx();

            AsyncNodeSuspendException ex = assertThrows(AsyncNodeSuspendException.class,
                () -> executor.execute(n, ctx));
            assertEquals("conditional:condCatch", ex.getWaitRef(),
                "waitRef namespaced: 'conditional:<nodeId>'");
            assertEquals(AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA, ex.getWaitType());
            assertEquals("approved == true", ex.getPayload().get("condition"));
        }
    }

    @Nested
    @DisplayName("Link")
    class Link {

        @Test
        @DisplayName("Given eventType=linkThrow + linkName + def.linkTargets 有 catch,When execute,Then 抛 BranchTransition")
        void link_throw_returns_branch_transition() throws Exception {
            // 构造 1 个流程:lt 节点 linkName=midway,def.linkTargets["midway"] = "lc"
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p1">
                    <bpmn:startEvent id="s"/>
                    <bpmn:intermediateCatchEvent id="lt"
                        ruleforge:eventType="linkThrow" ruleforge:linkName="midway"/>
                    <bpmn:intermediateCatchEvent id="lc"
                        ruleforge:eventType="linkCatch" ruleforge:linkName="midway"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="lt"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="lt" targetRef="end"/>
                    <bpmn:sequenceFlow id="e2" sourceRef="lc" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowNode lt = def.getNode("lt");
            FlowContext ctx = newCtx();
            ctx.setCurrentDef(def);

            IntermediateEventExecutor.BranchTransition bt =
                assertThrows(IntermediateEventExecutor.BranchTransition.class,
                    () -> executor.execute(lt, ctx));
            assertEquals("lc", bt.targetNodeId(),
                "linkThrow 'midway' should branch to linkCatch 'lc'");
        }

        @Test
        @DisplayName("Given linkThrow 找不到 linkCatch,When execute,Then 抛 FlowExecutionException")
        void link_throw_with_unmatched_link_name_throws() throws Exception {
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
            FlowNode lt = def.getNode("lt");
            FlowContext ctx = newCtx();
            ctx.setCurrentDef(def);

            FlowExecutionException ex = assertThrows(FlowExecutionException.class,
                () -> executor.execute(lt, ctx));
            assertTrue(ex.getMessage().toLowerCase().contains("link")
                    && (ex.getMessage().toLowerCase().contains("no matching")
                        || ex.getMessage().toLowerCase().contains("not found")
                        || ex.getMessage().toLowerCase().contains("unmatched")),
                "msg should mention link+no matching/not found/unmatched, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Given eventType=linkCatch,When execute,Then 透传 Continue(不抛)")
        void link_catch_passes_through() throws Exception {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:ruleforge="http://ruleforge.com/schema">
                  <bpmn:process id="p1">
                    <bpmn:startEvent id="s"/>
                    <bpmn:intermediateCatchEvent id="lc"
                        ruleforge:eventType="linkCatch" ruleforge:linkName="midway"/>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="e0" sourceRef="s" targetRef="lc"/>
                    <bpmn:sequenceFlow id="e1" sourceRef="lc" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
            FlowDefinition def = parser.parseSingleProcess(xml);
            FlowNode lc = def.getNode("lc");
            FlowContext ctx = newCtx();
            ctx.setCurrentDef(def);

            executor.execute(lc, ctx);
            // 不抛 = pass-through
        }
    }
}
