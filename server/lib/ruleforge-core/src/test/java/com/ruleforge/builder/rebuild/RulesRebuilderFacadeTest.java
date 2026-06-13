package com.ruleforge.builder.rebuild;

import com.ruleforge.builder.RulesRebuilder;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rule.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.48 — 5+1 facade 路由 BDD。
 *
 * <p>锁 4 件事:
 * <ol>
 *   <li>facade 持有 5 个 rebuilder(向导式/表/树/评分卡/DRL),顺序 DrlRuleRebuilder 最后</li>
 *   <li>4 stub 的 supports() 返 false(只 DrlRuleRebuilder supports)</li>
 *   <li>generic Rule instance 走 DrlRuleRebuilder fallback(LHS null 时不抛 NPE)</li>
 *   <li>所有 rebuilder 都不 supports 时(理论不可能,DrlRuleRebuilder 兜底)抛 IllegalStateException</li>
 * </ol>
 */
@DisplayName("V5.48 — RulesRebuilderFacade 5+1 路由")
class RulesRebuilderFacadeTest {

    private RulesRebuilder delegate;
    private RulesRebuilderFacade facade;

    @BeforeEach
    void setUp() {
        delegate = new RulesRebuilder();
        facade = new RulesRebuilderFacade(delegate);
    }

    @Nested
    @DisplayName("facade 持有 5 个 rebuilder")
    class FacadeComposition {

        @Test
        @DisplayName("5 个 rebuilder 都应被 facade 持有")
        void facadeHoldsFiveRebuilders() throws Exception {
            java.lang.reflect.Field f = RulesRebuilderFacade.class.getDeclaredField("rebuilders");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<RuleTypeRebuilder> list = (java.util.List<RuleTypeRebuilder>) f.get(facade);
            assertNotNull(list);
            assertTrue(list.size() >= 5,
                "facade 应至少 5 个 rebuilder,实际 " + list.size());
        }

        @Test
        @DisplayName("DrlRuleRebuilder 应在 list 末尾(plan 风险 R7 — fallback 顺序)")
        void drlRebuilderLastInList() throws Exception {
            java.lang.reflect.Field f = RulesRebuilderFacade.class.getDeclaredField("rebuilders");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<RuleTypeRebuilder> list = (java.util.List<RuleTypeRebuilder>) f.get(facade);
            RuleTypeRebuilder last = list.get(list.size() - 1);
            assertTrue(last instanceof DrlRuleRebuilder,
                "末尾 rebuilder 应是 DrlRuleRebuilder fallback,实际 " + last.getClass().getSimpleName());
        }
    }

    @Nested
    @DisplayName("3 stub rebuilder supports 返 false(不 active — V5.49.5/6/7 架构约束)")
    class StubsAreInactive {

        @Test
        @DisplayName("WizardRuleRebuilder.supports() 返 false(无独立 WizardRule class)")
        void wizardNotActive() {
            WizardRuleRebuilder w = new WizardRuleRebuilder();
            assertTrue(!w.supports(new Rule()));
        }

        @Test
        @DisplayName("DecisionTableRebuilder.supports() 返 false(DecisionTable 不 extends Rule)")
        void tableNotActive() {
            DecisionTableRebuilder t = new DecisionTableRebuilder();
            assertTrue(!t.supports(new Rule()));
        }

        @Test
        @DisplayName("TreeRebuilder.supports() 返 false(DecisionTree 不 extends Rule)")
        void treeNotActive() {
            TreeRebuilder t = new TreeRebuilder();
            assertTrue(!t.supports(new Rule()));
        }
    }

    @Nested
    @DisplayName("ScorecardRebuilder active — ScoreRule 是唯一 Rule 子类(V5.49.8)")
    class ScorecardRebuilderActive {

        @Test
        @DisplayName("ScorecardRebuilder.supports(ScoreRule) 返 true — instanceof 命中")
        void scorecardSupportsScoreRule() {
            ScorecardRebuilder s = new ScorecardRebuilder(delegate);
            com.ruleforge.model.scorecard.runtime.ScoreRule scoreRule = new com.ruleforge.model.scorecard.runtime.ScoreRule();
            assertTrue(s.supports(scoreRule), "ScoreRule 应被 supports() 识别");
        }

        @Test
        @DisplayName("ScorecardRebuilder.supports(plain Rule) 返 false — 普通 Rule 走 DrlRuleRebuilder")
        void scorecardDoesNotSupportPlainRule() {
            ScorecardRebuilder s = new ScorecardRebuilder(delegate);
            assertTrue(!s.supports(new Rule()));
        }

        @Test
        @DisplayName("ScorecardRebuilder.supports(LoopRule) 返 false — LoopRule 也走 DrlRuleRebuilder")
        void scorecardDoesNotSupportLoopRule() {
            ScorecardRebuilder s = new ScorecardRebuilder(delegate);
            com.ruleforge.model.rule.loop.LoopRule loopRule = new com.ruleforge.model.rule.loop.LoopRule();
            assertTrue(!s.supports(loopRule), "LoopRule 应走 DrlRuleRebuilder,不是 ScorecardRebuilder");
        }

        @Test
        @DisplayName("ScorecardRebuilder.delegate 是构造时传入的 RulesRebuilder 引用")
        void scorecardDelegateReferencePreserved() {
            ScorecardRebuilder s = new ScorecardRebuilder(delegate);
            assertSame(delegate, s.getDelegate());
        }

        @Test
        @DisplayName("facade.dispatchRebuild(ScoreRule) 走 ScorecardRebuilder,不抛 UOE")
        void facadeDispatchesScoreRuleToScorecard() {
            // Given — minimal ScoreRule
            com.ruleforge.model.scorecard.runtime.ScoreRule scoreRule = new com.ruleforge.model.scorecard.runtime.ScoreRule();
            scoreRule.setName("score-rule-test");
            com.ruleforge.model.rule.Rhs rhs = new com.ruleforge.model.rule.Rhs();
            rhs.setActions(java.util.Collections.emptyList());
            scoreRule.setRhs(rhs);

            // When & Then — 不应抛 UnsupportedOperationException(ScorecardRebuilder
            // 是 V5.49.8 唯一 active 的非 fallback rebuilder,delegate 调用 rebuildAction
            // 在空 actions 时不抛)
            assertDoesNotThrow(() -> facade.dispatchRebuild(scoreRule, new ResourceLibrary(), new HashMap<>(), false));
        }
    }

    @Nested
    @DisplayName("DrlRuleRebuilder fallback 行为")
    class DrlRebuilderFallback {

        @Test
        @DisplayName("minimal Rule(LHS null,空 RHS)dispatch 不抛")
        void dispatchMinimalRule() {
            // Given
            Rule r = new Rule();
            r.setName("facade-minimal");
            com.ruleforge.model.rule.Rhs rhs = new com.ruleforge.model.rule.Rhs();
            rhs.setActions(java.util.Collections.emptyList());
            r.setRhs(rhs);

            // When & Then
            assertDoesNotThrow(() -> facade.dispatchRebuild(r, new ResourceLibrary(), new HashMap<>(), false));
        }

        @Test
        @DisplayName("DrlRuleRebuilder.delegate 是构造时传入的 RulesRebuilder 引用")
        void delegateReferencePreserved() {
            DrlRuleRebuilder drl = new DrlRuleRebuilder(delegate);
            assertSame(delegate, drl.getDelegate());
        }
    }

    @Nested
    @DisplayName("facade 防御性 IllegalStateException(plan 风险 R7)")
    class FacadeDefensiveCheck {

        @Test
        @DisplayName("stub rebuilder.supports 强制 true 模拟 fallback 丢失,facade 抛 IllegalStateException")
        void facadeThrowsWhenNoMatch() {
            // Given — stub WizardRuleRebuilder,临时改 supports 返 true(模拟未来真支持向导)
            WizardRuleRebuilder stub = new WizardRuleRebuilder() {
                @Override
                public boolean supports(Rule rule) {
                    return true;
                }
            };
            // 把 stub 注入 facade
            RulesRebuilderFacade testFacade = new RulesRebuilderFacade(delegate) {
                // 不能 override final list,改用 reflection
            };
            try {
                java.lang.reflect.Field f = RulesRebuilderFacade.class.getDeclaredField("rebuilders");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<RuleTypeRebuilder> list = (java.util.List<RuleTypeRebuilder>) f.get(testFacade);
                // 移除 DrlRuleRebuilder 制造"全部不 supports"场景
                list.removeIf(r -> r instanceof DrlRuleRebuilder);
            } catch (Exception e) {
                throw new AssertionError("reflection setup failed", e);
            }

            // When & Then — facade 找不到 fallback 应抛 IllegalStateException
            Rule r = new Rule();
            r.setName("fallback-missing");
            assertThrows(IllegalStateException.class,
                () -> testFacade.dispatchRebuild(r, new ResourceLibrary(), new HashMap<>(), false));
        }
    }
}
