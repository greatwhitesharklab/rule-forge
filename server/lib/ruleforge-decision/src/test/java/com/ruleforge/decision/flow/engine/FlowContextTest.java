package com.ruleforge.decision.flow.engine;

import com.ruleforge.decision.flow.executor.MultiInstanceChildContext;
import com.ruleforge.decision.flow.ir.FlowDefinition;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.engine.KnowledgeSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * V5.39 A1 — FlowContext 行为规范。
 *
 * <p>3 BDD 分 3 组:4 typed handle 构造 / transient 字段读写 / MultiInstanceChildContext
 * 透传语义。
 */
@DisplayName("FlowContext 行为")
class FlowContextTest {

    private static FlowContext newCtx() {
        return new FlowContext(
            new FlowIdentity(UUID.randomUUID().toString(), "loan-v1", null),
            new BusinessVars(),
            new ReteSession(),
            new SuspendRegistry()
        );
    }

    @Nested
    @DisplayName("Group 1 — 4 typed handle 构造")
    class FourHandlesConstruction {

        @Test
        @DisplayName("Given 4 typed handle,When 构造,Then 4 个都能取到 + identity/vars 各自独立")
        void ctor_wires_four_handles() {
            FlowIdentity id = new FlowIdentity("run-1", "loan-v1", "p_credit");
            BusinessVars bv = new BusinessVars();
            bv.getVars().put("k", 1);
            ReteSession rs = new ReteSession();
            SuspendRegistry sr = new SuspendRegistry();

            FlowContext ctx = new FlowContext(id, bv, rs, sr);

            assertSame(id, ctx.identity());
            assertSame(bv, ctx.vars());
            assertSame(rs, ctx.rete());
            assertSame(sr, ctx.suspend());
            // identity 字段能取
            assertEquals("run-1", ctx.identity().flowRunId());
            assertEquals("loan-v1", ctx.identity().flowId());
            assertEquals("p_credit", ctx.identity().currentPoolId());
            // vars 字段能取
            assertEquals(1, ctx.vars().getVars().get("k"));
        }

        @Test
        @DisplayName("Given 缺任一 handle(null),When 构造,Then 抛 IllegalArgumentException")
        void ctor_rejects_null_handles() {
            FlowIdentity id = new FlowIdentity("r", "f", null);
            BusinessVars bv = new BusinessVars();
            ReteSession rs = new ReteSession();
            SuspendRegistry sr = new SuspendRegistry();
            assertThrows(IllegalArgumentException.class, () -> new FlowContext(null, bv, rs, sr));
            assertThrows(IllegalArgumentException.class, () -> new FlowContext(id, null, rs, sr));
            assertThrows(IllegalArgumentException.class, () -> new FlowContext(id, bv, null, sr));
            assertThrows(IllegalArgumentException.class, () -> new FlowContext(id, bv, rs, null));
        }

        @Test
        @DisplayName("Given newDefault(flowId),Then 返回完整 ctx + UUID flowRunId + 其它 3 handle 非 null")
        void newDefault_factory_produces_complete_ctx() {
            FlowContext ctx = FlowContext.newDefault("loan-v1");
            assertNotNull(ctx);
            assertNotNull(ctx.identity());
            assertNotNull(ctx.identity().flowRunId());
            assertEquals("loan-v1", ctx.identity().flowId());
            assertNotNull(ctx.vars());
            assertNotNull(ctx.rete());
            assertNotNull(ctx.suspend());
        }
    }

    @Nested
    @DisplayName("Group 2 — transient 字段读写")
    class TransientFields {

        @Test
        @DisplayName("Given 新建,When 读 activeTokens/joinArrivals/joinedTokens,Then 都是非 null 空容器")
        void transient_containers_start_empty() {
            FlowContext ctx = newCtx();
            assertNotNull(ctx.activeTokens());
            assertTrue(ctx.activeTokens().isEmpty());
            assertNotNull(ctx.joinArrivals());
            assertTrue(ctx.joinArrivals().isEmpty());
            assertNotNull(ctx.joinedTokens());
            assertTrue(ctx.joinedTokens().isEmpty());
            // currentToken 初始 null
            assertNull(ctx.currentToken());
        }

        @Test
        @DisplayName("Given currentToken / currentDef / currentBpmn,When set,Then 能 get 回来")
        void current_refs_roundtrip() {
            FlowContext ctx = newCtx();
            Token token = new Token("tok-1");
            FlowDefinition def = mock(FlowDefinition.class);
            com.ruleforge.decision.flow.ir.BpmnDefinition bpmn =
                mock(com.ruleforge.decision.flow.ir.BpmnDefinition.class);
            ctx.setCurrentToken(token);
            ctx.setCurrentDef(def);
            ctx.setCurrentBpmn(bpmn);
            assertSame(token, ctx.currentToken());
            assertSame(def, ctx.currentDef());
            assertSame(bpmn, ctx.currentBpmn());
        }
    }

    @Nested
    @DisplayName("Group 3 — MultiInstanceChildContext 透传语义")
    class MultiInstanceChild {

        @Test
        @DisplayName("Given 父 ctx + childVars,When 构造 MIC,Then vars() 共享父 BusinessVars(写直接落到父)")
        void child_shares_vars_map_with_parent() {
            // V5.39 A1 v0.1 简化:MIC vars() = 父的 BusinessVars(共享 map 引用)。
            // 理由:parallel MI 的"隔离"由 branch 各自的 item + outputs 收集承担,
            // 父 vars 不是 fork/join worklist,inner 写父 vars 反而是 caller 预期
            // ("item", "outputs" 在父上看到)。
            FlowContext parent = newCtx();
            parent.vars().getVars().put("parent_key", "parent_value");
            Map<String, Object> childVars = new HashMap<>();
            childVars.put("element", "item-1");

            MultiInstanceChildContext mic = new MultiInstanceChildContext(parent, childVars);

            // child vars() == 父的 BusinessVars(同引用)
            assertSame(parent.vars(), mic.vars());
            // 父的 parent_key 可见
            assertEquals("parent_value", mic.vars().getVars().get("parent_key"));
            // child 写"new_key"直接落到父(MIC 共享引用)
            mic.vars().getVars().put("new_key", 42);
            assertEquals(42, parent.vars().getVars().get("new_key"));
        }

        @Test
        @DisplayName("Given 父 ctx + childVars,When MIC 构造,Then identity/rete/suspend 共享父的")
        void child_shares_identity_rete_suspend_with_parent() {
            FlowContext parent = newCtx();
            // 父 rete 装点东西
            parent.rete().getInsertedEntities().add(new GeneralEntity("FakeEntity"));

            MultiInstanceChildContext mic = new MultiInstanceChildContext(parent, new HashMap<>());

            assertSame(parent.identity(), mic.identity());
            assertSame(parent.rete(), mic.rete());
            assertSame(parent.suspend(), mic.suspend());
            // insertedEntities 父修改,子能看到(同一引用)
            assertEquals(1, mic.rete().getInsertedEntities().size());
        }

        @Test
        @DisplayName("Given MIC,When 父 setCurrentToken,Then MIC.currentToken 仍是构造时透传的(独立持有)")
        void child_currentToken_isolated_from_parent_setter() {
            FlowContext parent = newCtx();
            Token parentToken = new Token("parent-tok");
            parent.setCurrentToken(parentToken);

            MultiInstanceChildContext mic = new MultiInstanceChildContext(parent, new HashMap<>());
            // MIC 构造时透传了 parent 的 currentToken
            assertSame(parentToken, mic.currentToken());

            // 父后续 setCurrentToken 改成另一个,mic 不受影响(mic 持有自己的引用)
            Token newParentToken = new Token("new-tok");
            parent.setCurrentToken(newParentToken);
            assertNotSame(newParentToken, mic.currentToken());
            assertSame(parentToken, mic.currentToken());
        }
    }
}
