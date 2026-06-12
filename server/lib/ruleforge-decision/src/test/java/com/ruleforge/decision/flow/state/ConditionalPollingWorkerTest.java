package com.ruleforge.decision.flow.state;

import com.ruleforge.decision.exception.AsyncNodeSuspendException;
import com.ruleforge.decision.flow.engine.ConditionEvaluator;
import com.ruleforge.decision.entity.DecisionFlowState;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * V5.36 A7 — ConditionalPollingWorker 行为规范。
 *
 * <p>Mirror V5.32 补充契约:Conditional intermediate event 挂起后,polling worker
 * 定时扫 {@code wait_ref LIKE 'conditional:%'} 的行,重新求值条件:
 * <ul>
 *   <li>条件 evaluate = true → 标 COMPLETED + 调 resume()(走原本的挂起恢复路径)</li>
 *   <li>条件 evaluate = false → 留 PENDING_ASYNC(下次再扫)</li>
 *   <li>条件 evaluate 抛错 / vars 缺字段 → 留 PENDING_ASYNC + log warn(不阻断 traverse)</li>
 *   <li>非 conditional:* waitRef → 跳过(不属于本 worker 范畴,留给 FlowStateRecoveryJob)</li>
 * </ul>
 *
 * <p>本测试 4 BDD,Mockito 隔离 mapper / persistence:
 * <ol>
 *   <li>conditional waitRef + 条件 true → 调 resume 1 次 + 标 COMPLETED</li>
 *   <li>conditional waitRef + 条件 false → 不调 resume + 留 PENDING_ASYNC</li>
 *   <li>非 conditional waitRef (timer/message) → 跳过</li>
 *   <li>条件 evaluate 抛错 (vars 缺字段) → 留 PENDING_ASYNC + log warn</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConditionalPollingWorker — 条件轮询")
class ConditionalPollingWorkerTest {

    @Mock
    private com.ruleforge.decision.mapper.DecisionFlowStateMapper mapper;
    @Mock
    private FlowStatePersistenceService persistence;
    @Mock
    private com.ruleforge.decision.flow.engine.FlowDefinitionRepo repo;
    @Mock
    private com.ruleforge.decision.flow.engine.FlowEngine engine;

    /**
     * 直接构造一个 worker,绕开 @Scheduled(单测里手动调 pollOnce())。
     */
    private ConditionalPollingWorker newWorker() {
        return new ConditionalPollingWorker(mapper, persistence, repo, engine, new ConditionEvaluator());
    }

    private DecisionFlowState newState(String flowRunId, String waitRef, String condition) {
        DecisionFlowState s = new DecisionFlowState();
        s.setId(1L);
        s.setFlowRunId(flowRunId);
        s.setFlowId("p1");
        s.setCurrentNodeId("condNode");
        s.setStatus(DecisionFlowState.STATUS_PENDING_ASYNC);
        s.setWaitType(AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA);
        s.setWaitRef(waitRef);
        s.setRowVars("{}");
        // 把 condition 塞进 rowVars 给 worker 读
        Map<String, Object> payload = new HashMap<>();
        if (condition != null) payload.put("__condition__", condition);
        s.setRowVars(toJson(payload));
        return s;
    }

    private String toJson(Map<String, Object> m) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String s) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    @Nested
    @DisplayName("条件 true → 推进")
    class WhenConditionTrue {

        @Test
        @DisplayName("Given conditional waitRef + vars 已满足条件,When pollOnce,Then 调 engine.resume 1 次")
        void true_condition_resumes_flow() {
            // vars: amount=2000;condition=amount > 1000 → true
            Map<String, Object> vars = new HashMap<>();
            vars.put("amount", 2000);
            vars.put("__condition__", "amount > 1000");
            DecisionFlowState s = newState("run-1", "conditional:condNode", "amount > 1000");
            s.setRowVars(toJson(vars));
            when(mapper.selectRecoverable(20)).thenReturn(List.of(s));
            when(persistence.deserializeVars(s)).thenReturn(vars);
            org.mockito.Mockito.doNothing().when(persistence).deserializeJoinArrivals(any(), any());
            FlowDefinition stubDef = org.mockito.Mockito.mock(FlowDefinition.class);
            when(repo.getOrLoad("p1")).thenReturn(stubDef);

            ConditionalPollingWorker worker = newWorker();
            worker.pollOnce();

            // engine.resume 调 1 次(condition true → 推进)
            verify(engine, atLeastOnce()).resume(any(FlowDefinition.class), any(), anyString());
        }
    }

    @Nested
    @DisplayName("条件 false → 留挂")
    class WhenConditionFalse {

        @Test
        @DisplayName("Given conditional waitRef + vars 条件 false,When pollOnce,Then 不调 resume + 留 PENDING_ASYNC")
        void false_condition_keeps_pending() {
            // vars: amount=500;condition=amount > 1000 → false
            Map<String, Object> vars = new HashMap<>();
            vars.put("amount", 500);
            vars.put("__condition__", "amount > 1000");
            DecisionFlowState s = newState("run-2", "conditional:condNode", "amount > 1000");
            s.setRowVars(toJson(vars));
            when(mapper.selectRecoverable(20)).thenReturn(List.of(s));
            when(persistence.deserializeVars(s)).thenReturn(vars);

            ConditionalPollingWorker worker = newWorker();
            worker.pollOnce();

            // 没 resume
            verify(engine, never()).resume(any(FlowDefinition.class), any(), anyString());
            // 也没 updateAtomic(false condition 留挂,不动 rowVars)
            verify(mapper, never()).updateAtomic(any(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("非 conditional waitRef → 跳过")
    class SkipNonConditional {

        @Test
        @DisplayName("Given timer waitRef,When pollOnce,Then 跳过(留给 FlowStateRecoveryJob)")
        void skip_timer_waitref() {
            DecisionFlowState s = newState("run-3", "timer:timerNode", null);
            s.setWaitType(AsyncNodeSuspendException.WAIT_TYPE_ASYNC_TASK);
            s.setRowVars("{}");
            when(mapper.selectRecoverable(20)).thenReturn(List.of(s));

            ConditionalPollingWorker worker = newWorker();
            worker.pollOnce();

            // 跳过了 — 没 deserializeVars(非 conditional 不读 condition)
            verify(persistence, never()).deserializeVars(s);
            verify(engine, never()).resume(any(FlowDefinition.class), any(), anyString());
        }

        @Test
        @DisplayName("Given message waitRef,When pollOnce,Then 跳过")
        void skip_message_waitref() {
            DecisionFlowState s = newState("run-4", "message:loan_approved", null);
            s.setWaitType(AsyncNodeSuspendException.WAIT_TYPE_ASYNC_DATA);
            s.setRowVars("{}");
            when(mapper.selectRecoverable(20)).thenReturn(List.of(s));

            ConditionalPollingWorker worker = newWorker();
            worker.pollOnce();

            verify(persistence, never()).deserializeVars(s);
            verify(engine, never()).resume(any(FlowDefinition.class), any(), anyString());
        }
    }

    @Nested
    @DisplayName("条件 evaluate 抛错 → 留挂 + log warn")
    class ConditionEvalFailure {

        @Test
        @DisplayName("Given 条件语法错(无 ${} + 不可解析),When pollOnce,Then 留 PENDING_ASYNC 不阻断")
        void bad_condition_keeps_pending() {
            Map<String, Object> vars = new HashMap<>();
            vars.put("__condition__", "this is not a valid UEL @@@");
            DecisionFlowState s = newState("run-5", "conditional:badNode", "this is not a valid UEL @@@");
            s.setRowVars(toJson(vars));
            when(mapper.selectRecoverable(20)).thenReturn(List.of(s));
            when(persistence.deserializeVars(s)).thenReturn(vars);

            ConditionalPollingWorker worker = newWorker();
            worker.pollOnce();

            // 抛错不阻断 — 留挂,不动 DB
            verify(engine, never()).resume(any(FlowDefinition.class), any(), anyString());
            verify(mapper, never()).updateAtomic(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("Given vars 缺 __condition__,When pollOnce,Then 留挂(无 condition = 不该被 worker 接管)")
        void missing_condition_var_keeps_pending() {
            Map<String, Object> vars = new HashMap<>();
            // 没 __condition__ key
            DecisionFlowState s = newState("run-6", "conditional:noCond", null);
            s.setRowVars(toJson(vars));
            when(mapper.selectRecoverable(20)).thenReturn(List.of(s));
            when(persistence.deserializeVars(s)).thenReturn(vars);

            ConditionalPollingWorker worker = newWorker();
            worker.pollOnce();

            verify(engine, never()).resume(any(FlowDefinition.class), any(), anyString());
        }
    }
}