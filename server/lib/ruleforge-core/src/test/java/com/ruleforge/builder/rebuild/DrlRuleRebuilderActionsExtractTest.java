package com.ruleforge.builder.rebuild;

import com.ruleforge.action.Action;
import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Other;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.loop.LoopEnd;
import com.ruleforge.model.rule.loop.LoopRule;
import com.ruleforge.model.rule.loop.LoopStart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V6.9.28 — {@link DrlRuleRebuilder#rebuild} 4-way actions extract 契约 BDD。
 *
 * <p>锁 V6.9.28 收口 (DrlRuleRebuilder.java L56-89: 4 method-local copies of
 * `if (X != null && X.getActions() != null) { for (Action a : X.getActions()) {
 * delegate.rebuildAction(...); }}` → private {@code rebuildActions(List, ...)} helper)
 * 的行为不变性:
 * <ul>
 *   <li><b>Rhs actions</b>: rebuildAction 调用 N 次(N = actions.size())</li>
 *   <li><b>Other actions</b>: rebuildAction 调用 N 次</li>
 *   <li><b>LoopStart actions</b>: rebuildAction 调用 N 次 (LoopRule only)</li>
 *   <li><b>LoopEnd actions</b>: rebuildAction 调用 N 次 (LoopRule only)</li>
 *   <li><b>null actions 集合</b>: rebuildAction 不调</li>
 *   <li><b>4 path 之间调用顺序保留</b>: Rhs → Other → (LoopStart → LoopEnd)</li>
 * </ul>
 *
 * <p><b>Why V6.9.28</b>: 4 method 4 行 100% 同构 pattern, V6.9.14 helper extract
 * 模式直接套; 9 行净, build-time only, pure refactor。
 */
@DisplayName("V6.9.28 — DrlRuleRebuilder.rebuild() 4-way actions extract 契约")
class DrlRuleRebuilderActionsExtractTest {

    private RulesRebuilder delegate;
    private DrlRuleRebuilder rebuilder;

    @BeforeEach
    void setUp() {
        delegate = mock(RulesRebuilder.class);
        rebuilder = new DrlRuleRebuilder(delegate);
    }

    private static List<Action> actions(int count) {
        List<Action> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(mock(Action.class));
        }
        return list;
    }

    private static ResourceLibrary lib() {
        return mock(ResourceLibrary.class);
    }

    private static Rule plainRule(Rhs rhs, Other other) {
        Rule r = mock(Rule.class);
        when(r.getLhs()).thenReturn(null);
        when(r.getRhs()).thenReturn(rhs);
        when(r.getOther()).thenReturn(other);
        return r;
    }

    // ====== Rhs / Other path ======

    @Nested
    @DisplayName("非 LoopRule — Rhs + Other 路径")
    class NonLoopRulePath {

        @Test
        @DisplayName("Rhs 有 3 actions + Other 有 2 actions → delegate.rebuildAction 调用 5 次")
        void rhsAndOtherBothPresent() {
            Rhs rhs = mock(Rhs.class);
            when(rhs.getActions()).thenReturn(actions(3));
            Other other = mock(Other.class);
            when(other.getActions()).thenReturn(actions(2));
            Rule rule = plainRule(rhs, other);

            rebuilder.rebuild(rule, lib(), new HashMap<>(), false);

            verify(delegate, times(5)).rebuildAction(any(Action.class), any(), anyMap(), anyBoolean());
        }

        @Test
        @DisplayName("Rhs + Other 都 null actions → rebuildAction 不调")
        void bothNullActionsSkipped() {
            Rhs rhs = mock(Rhs.class);
            when(rhs.getActions()).thenReturn(null);
            Other other = mock(Other.class);
            when(other.getActions()).thenReturn(null);
            Rule rule = plainRule(rhs, other);

            rebuilder.rebuild(rule, lib(), new HashMap<>(), false);

            verify(delegate, never()).rebuildAction(any(Action.class), any(), anyMap(), anyBoolean());
        }

        @Test
        @DisplayName("Rhs actions 调完后 Other actions 调 — 调用顺序保留")
        void orderPreserved() {
            Rhs rhs = mock(Rhs.class);
            Action rhsAction = mock(Action.class);
            when(rhs.getActions()).thenReturn(List.of(rhsAction));
            Other other = mock(Other.class);
            Action otherAction = mock(Action.class);
            when(other.getActions()).thenReturn(List.of(otherAction));
            Rule rule = plainRule(rhs, other);

            rebuilder.rebuild(rule, lib(), new HashMap<>(), false);

            // InOrder verification: Rhs action called before Other action
            org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(delegate);
            inOrder.verify(delegate).rebuildAction(eq(rhsAction), any(), anyMap(), anyBoolean());
            inOrder.verify(delegate).rebuildAction(eq(otherAction), any(), anyMap(), anyBoolean());
        }
    }

    // ====== LoopRule path ======

    @Nested
    @DisplayName("LoopRule — LoopStart + LoopEnd 路径")
    class LoopRulePath {

        @Test
        @DisplayName("LoopRule + Rhs(2) + Other(1) + LoopStart(2) + LoopEnd(3) → 8 次调用")
        void allFourPathsFire() {
            LoopRule loopRule = mock(LoopRule.class);
            Rhs rhs = mock(Rhs.class);
            when(rhs.getActions()).thenReturn(actions(2));
            Other other = mock(Other.class);
            when(other.getActions()).thenReturn(actions(1));
            LoopStart start = mock(LoopStart.class);
            when(start.getActions()).thenReturn(actions(2));
            LoopEnd end = mock(LoopEnd.class);
            when(end.getActions()).thenReturn(actions(3));
            when(loopRule.getRhs()).thenReturn(rhs);
            when(loopRule.getOther()).thenReturn(other);
            when(loopRule.getLoopStart()).thenReturn(start);
            when(loopRule.getLoopEnd()).thenReturn(end);
            when(loopRule.getLhs()).thenReturn(null);

            rebuilder.rebuild(loopRule, lib(), new HashMap<>(), false);

            verify(delegate, times(8)).rebuildAction(any(Action.class), any(), anyMap(), anyBoolean());
        }

        @Test
        @DisplayName("LoopRule 但 LoopStart.actions == null → rebuildAction 不调 LoopStart path")
        void loopStartNullActionsSkipped() {
            LoopRule loopRule = mock(LoopRule.class);
            when(loopRule.getLhs()).thenReturn(null);
            when(loopRule.getRhs()).thenReturn(null);
            when(loopRule.getOther()).thenReturn(null);
            LoopStart start = mock(LoopStart.class);
            when(start.getActions()).thenReturn(null);
            LoopEnd end = mock(LoopEnd.class);
            when(end.getActions()).thenReturn(actions(2));
            when(loopRule.getLoopStart()).thenReturn(start);
            when(loopRule.getLoopEnd()).thenReturn(end);

            rebuilder.rebuild(loopRule, lib(), new HashMap<>(), false);

            verify(delegate, times(2)).rebuildAction(any(Action.class), any(), anyMap(), anyBoolean());
        }
    }

    // ====== namedMap + forDSL 参数透传 ======

    @Nested
    @DisplayName("参数透传")
    class ParamPassing {

        @Test
        @DisplayName("namedMap + forDSL=true 透传到 delegate.rebuildAction")
        void paramsPassedThrough() {
            Rhs rhs = mock(Rhs.class);
            Action act = mock(Action.class);
            when(rhs.getActions()).thenReturn(List.of(act));
            Other other = mock(Other.class);
            when(other.getActions()).thenReturn(null);
            Rule rule = plainRule(rhs, other);

            Map<String, String> namedMap = new HashMap<>();
            namedMap.put("old", "new");
            rebuilder.rebuild(rule, lib(), namedMap, true);

            ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<Boolean> dslCaptor = ArgumentCaptor.forClass(Boolean.class);
            verify(delegate, atLeastOnce()).rebuildAction(any(Action.class), any(), mapCaptor.capture(), dslCaptor.capture());
            assertThat(mapCaptor.getValue()).containsEntry("old", "new");
            assertThat(dslCaptor.getValue()).isTrue();
        }
    }
}
