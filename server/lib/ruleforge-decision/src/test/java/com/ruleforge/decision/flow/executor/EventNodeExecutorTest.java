package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.exception.FlowExecutionException;
import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.decision.flow.engine.Token;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.decision.flow.ir.FlowNode;
import com.ruleforge.decision.flow.ir.NodeType;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.34 A2 — EventNodeExecutor 端到端行为。
 *
 * <p>Mirror Rust V5.30 {@code end_event.rs}:None → Continue(无 thrownError);
 * Error/Escalation/Terminate → 写 ctx.thrownError + 抛 FlowExecutionException。
 */
@DisplayName("EventNodeExecutor 端到端")
class EventNodeExecutorTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();
    private final EventNodeExecutor executor = new EventNodeExecutor();

    /** V5.33 A0:ctx.getVars() 委托 currentToken;init token 让 put 走 token.vars。 */
    private FlowContext newCtx() {
        FlowContext ctx = new FlowContext();
        ctx.setFlowRunId("test-" + System.nanoTime());
        Token t = new Token("tok-" + System.nanoTime());
        ctx.getActiveTokens().add(t);
        ctx.setCurrentToken(t);
        return ctx;
    }

    private FlowNode parseEnd(String attrsBlock) {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:ruleforge="http://ruleforge.com/schema">
              <bpmn:process id="p1">
                <bpmn:startEvent id="s"/>
                <bpmn:endEvent id="end" %s/>
                <bpmn:sequenceFlow id="f0" sourceRef="s" targetRef="end"/>
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(attrsBlock);
        FlowDefinition def = parser.parseSingleProcess(xml);
        return def.getNode("end");
    }

    @Test
    @DisplayName("Given 正常 end (无 endType),When execute,Then 不抛异常,ctx.thrownError=null")
    void normal_end_does_not_throw() throws Exception {
        FlowContext ctx = newCtx();
        executor.execute(parseEnd(""), ctx);
        assertNull(ctx.getThrownError());
    }

    @Test
    @DisplayName("Given endType=error + errorRef=REF_E,When execute,Then 抛 FlowExecutionException,ctx.thrownError=REF_E")
    void error_end_writes_thrown_error_and_throws() {
        FlowContext ctx = newCtx();
        FlowNode end = parseEnd("ruleforge:endType=\"error\" ruleforge:errorRef=\"REF_E\"");
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
            () -> executor.execute(end, ctx));
        assertEquals("REF_E", ctx.getThrownError());
        // msg 应包含 REF_E + "error" 关键词
        org.junit.jupiter.api.Assertions.assertTrue(
            ex.getMessage().contains("REF_E") || ex.getMessage().toLowerCase().contains("error"),
            "msg should mention REF_E or error, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Given endType=escalation + escalationRef=REF_S,When execute,Then 抛 FlowExecutionException,ctx.thrownError=REF_S")
    void escalation_end_writes_thrown_error_and_throws() {
        FlowContext ctx = newCtx();
        FlowNode end = parseEnd("ruleforge:endType=\"escalation\" ruleforge:escalationRef=\"REF_S\"");
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
            () -> executor.execute(end, ctx));
        assertEquals("REF_S", ctx.getThrownError());
        org.junit.jupiter.api.Assertions.assertTrue(
            ex.getMessage().contains("REF_S") || ex.getMessage().toLowerCase().contains("escalation"),
            "msg should mention REF_S or escalation, got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Given endType=terminate,When execute,Then 抛 FlowExecutionException,msg 含 'terminate'")
    void terminate_end_throws() {
        FlowContext ctx = newCtx();
        FlowNode end = parseEnd("ruleforge:endType=\"terminate\"");
        FlowExecutionException ex = assertThrows(FlowExecutionException.class,
            () -> executor.execute(end, ctx));
        // V5.30 v0:terminate 跟 Error 同 path(Rust 端还没 token-kill,留 P1)
        // ctx.thrownError 应该是 null(terminate 不带 ref)
        assertNull(ctx.getThrownError());
        assertTrue(ex.getMessage().toLowerCase().contains("terminate"),
            "msg should contain 'terminate', got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Given start event,When execute,Then 不抛异常(只 log)")
    void start_event_does_not_throw() throws Exception {
        FlowContext ctx = newCtx();
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:ruleforge="http://ruleforge.com/schema">
              <bpmn:process id="p1">
                <bpmn:startEvent id="s"/>
                <bpmn:endEvent id="e"/>
              </bpmn:process>
            </bpmn:definitions>
            """;
        FlowDefinition def = parser.parseSingleProcess(xml);
        executor.execute(def.getNode("s"), ctx);
        // 仅 log,无副作用
    }
}
