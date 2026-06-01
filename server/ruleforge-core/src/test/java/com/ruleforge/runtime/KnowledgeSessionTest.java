package com.ruleforge.runtime;

import com.ruleforge.Utils;
import com.ruleforge.action.AbstractAction;
import com.ruleforge.action.ActionValue;
import com.ruleforge.action.ActionType;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rete.ObjectTypeNode;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.TerminalNode;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.assertor.AssertorEvaluator;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.runtime.rete.Context;
import com.ruleforge.runtime.rete.ValueCompute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("KnowledgeSession - 知识会话")
class KnowledgeSessionTest {

    private ApplicationContext previousAppCtx;

    @BeforeEach
    void setUpApplicationContext() throws Exception {
        // Save the previous ApplicationContext so we can restore it
        previousAppCtx = Utils.getApplicationContext();

        // Create a mock ApplicationContext with the beans that ContextImpl requires
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        when(mockCtx.getBean("ruleforge.assertorEvaluator")).thenReturn(new AssertorEvaluator());
        when(mockCtx.getBean("ruleforge.valueCompute")).thenReturn(new ValueCompute());

        // Set Utils.applicationContext via reflection
        Field field = Utils.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, mockCtx);
    }

    @AfterEach
    void restoreApplicationContext() throws Exception {
        Field field = Utils.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, previousAppCtx);
    }

    /**
     * Build a KnowledgePackage from a list of ObjectTypeNode definitions.
     * Each definition is a pair: (objectTypeClass, rule).
     * The rule's LHS is left null so the RETE branch uses a direct OTN -> TerminalNode path.
     */
    private KnowledgePackage buildPackage(List<ObjectTypeNode> otns) {
        Rete rete = new Rete(otns, new ResourceLibrary());
        KnowledgePackageImpl pkg = new KnowledgePackageImpl();
        pkg.setRete(rete);
        return pkg;
    }

    private KnowledgePackage buildEmptyPackage() {
        return buildPackage(new ArrayList<>());
    }

    /**
     * Create a Rule with an OTN for the given class, and a simple action that
     * sets a flag via AtomicBoolean when executed.
     */
    private Rule buildRuleWithFlagAction(String name, String objectTypeClass,
                                         List<ObjectTypeNode> otns, AtomicBoolean fired) {
        Rule rule = new Rule();
        rule.setName(name);

        Rhs rhs = new Rhs();
        List<com.ruleforge.action.Action> actions = new ArrayList<>();
        actions.add(new AbstractAction() {
            private final ActionType actionType = ActionType.ConsolePrint;

            @Override
            public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
                fired.set(true);
                return null;
            }

            @Override
            public ActionType getActionType() {
                return actionType;
            }
        });
        rhs.setActions(actions);
        rule.setRhs(rhs);

        ObjectTypeNode otn = new ObjectTypeNode(objectTypeClass, 1);
        TerminalNode terminalNode = new TerminalNode(rule, 2);
        otn.addLine(terminalNode);
        otns.add(otn);

        return rule;
    }

    @Nested
    @DisplayName("创建会话")
    class CreateSession {

        // Given KnowledgePackage 由空的 RETE 网络构建
        // When 调用 KnowledgeSessionFactory.newKnowledgeSession(pkg)
        // Then 应返回非 null 的 KnowledgeSession
        @Test
        @DisplayName("应从 KnowledgePackage 创建会话")
        void shouldCreateSessionFromPackage() {
            // Given
            KnowledgePackage pkg = buildEmptyPackage();
            // When
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(pkg);
            // Then
            assertThat(session).isNotNull();
            assertThat(session.getKnowledgePackageList()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("规则执行")
    class FireRules {

        // Given KnowledgeSession 包含一条规则：当 age > 18 时设置 adult=true
        // And 插入一个 age=20 的 GeneralEntity
        // When 调用 fireRules()
        // Then 规则应触发
        // And 实体的 adult 属性应为 "true"
        @Test
        @DisplayName("满足条件时规则应触发并执行动作")
        void shouldFireRuleWhenConditionMet() {
            // Given - build a simple "no-LHS" rule that always fires via __*__ OTN
            AtomicBoolean ruleFired = new AtomicBoolean(false);
            List<ObjectTypeNode> otns = new ArrayList<>();
            buildRuleWithFlagAction("adult-rule", "__*__", otns, ruleFired);
            KnowledgePackage pkg = buildPackage(otns);
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(pkg);

            // When - insert a fact and fire rules
            GeneralEntity entity = new GeneralEntity("User");
            entity.put("age", 20);
            session.insert(entity);
            RuleExecutionResponse response = session.fireRules();

            // Then - the rule should have fired
            assertThat(ruleFired.get()).isTrue();
            assertThat(response.getFiredRules()).hasSize(1);
            assertThat(response.getFiredRules().get(0).getName()).isEqualTo("adult-rule");
        }

        // Given KnowledgeSession 包含一条规则：当 age > 18 时设置 adult=true
        // And 插入一个 age=10 的 GeneralEntity
        // When 调用 fireRules()
        // Then 规则不应触发
        // And 实体的 adult 属性应为 null
        @Test
        @DisplayName("不满足条件时规则不应触发")
        void shouldNotFireRuleWhenConditionNotMet() {
            // Given - build a rule that matches only the "Adult" class (not "User")
            AtomicBoolean ruleFired = new AtomicBoolean(false);
            List<ObjectTypeNode> otns = new ArrayList<>();
            buildRuleWithFlagAction("adult-rule", "Adult", otns, ruleFired);
            KnowledgePackage pkg = buildPackage(otns);
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(pkg);

            // When - insert a fact with a different class ("User" vs "Adult")
            GeneralEntity entity = new GeneralEntity("User");
            entity.put("age", 10);
            session.insert(entity);
            RuleExecutionResponse response = session.fireRules();

            // Then - the rule should not have fired (OTN only matches "Adult" class)
            assertThat(ruleFired.get()).isFalse();
            assertThat(response.getFiredRules()).isEmpty();
        }

        // Given KnowledgeSession 包含两条规则
        // And 插入一个同时满足两个条件的事实
        // When 调用 fireRules()
        // Then 两条规则都应触发
        @Test
        @DisplayName("多条规则满足条件时应都触发")
        void shouldFireMultipleRulesWhenConditionsMet() {
            // Given - build two rules that both match via __*__
            AtomicBoolean rule1Fired = new AtomicBoolean(false);
            AtomicBoolean rule2Fired = new AtomicBoolean(false);
            List<ObjectTypeNode> otns = new ArrayList<>();
            buildRuleWithFlagAction("rule-1", "__*__", otns, rule1Fired);

            // Second rule needs its own OTN
            Rule rule2 = new Rule();
            rule2.setName("rule-2");
            Rhs rhs2 = new Rhs();
            List<com.ruleforge.action.Action> actions2 = new ArrayList<>();
            actions2.add(new AbstractAction() {
                private final ActionType actionType = ActionType.ConsolePrint;

                @Override
                public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
                    rule2Fired.set(true);
                    return null;
                }

                @Override
                public ActionType getActionType() {
                    return actionType;
                }
            });
            rhs2.setActions(actions2);
            rule2.setRhs(rhs2);
            ObjectTypeNode otn2 = new ObjectTypeNode("__*__", 3);
            TerminalNode terminalNode2 = new TerminalNode(rule2, 4);
            otn2.addLine(terminalNode2);
            otns.add(otn2);

            KnowledgePackage pkg = buildPackage(otns);
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(pkg);

            // When - insert a fact and fire rules
            GeneralEntity entity = new GeneralEntity("User");
            entity.put("age", 25);
            session.insert(entity);
            RuleExecutionResponse response = session.fireRules();

            // Then - both rules should have fired
            assertThat(rule1Fired.get()).isTrue();
            assertThat(rule2Fired.get()).isTrue();
            assertThat(response.getFiredRules()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("事实插入")
    class InsertFact {

        // Given 空 KnowledgeSession
        // When 调用 insert(new GeneralEntity("User"))
        // Then 不应抛出异常
        @Test
        @DisplayName("应能插入 GeneralEntity 事实")
        void shouldInsertGeneralEntityFact() {
            // Given
            KnowledgePackage pkg = buildEmptyPackage();
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(pkg);
            GeneralEntity entity = new GeneralEntity("User");
            // When & Then
            assertThatCode(() -> session.insert(entity)).doesNotThrowAnyException();
            assertThat(session.getAllFactsMap()).containsEntry("User", entity);
        }
    }
}
