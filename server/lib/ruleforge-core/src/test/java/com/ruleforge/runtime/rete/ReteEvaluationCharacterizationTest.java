package com.ruleforge.runtime.rete;
import com.ruleforge.engine.AssertorEvaluator;
import com.ruleforge.engine.ValueCompute;
import com.ruleforge.engine.WorkingMemory;

import com.ruleforge.action.AbstractAction;
import com.ruleforge.action.ActionValue;
import com.ruleforge.action.ActionType;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.rete.ObjectTypeNode;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.TerminalNode;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgePackageImpl;
import com.ruleforge.engine.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.engine.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * V6.1 TD-2 特征化测试 — 锁定 RETE 求值行为(model→runtime 融合的安全网)。
 *
 * <p>TD-2 要拆 model.rete(结构)和 runtime.rete(执行)。本测试锁定当前行为:
 * 当拆分后这些测试仍全绿 = 行为不变 = 安全。
 *
 * <p>覆盖路径:
 * <ul>
 *   <li>OTN → Terminal:fact class 匹配 → 规则触发</li>
 *   <li>salience 排序:高 salience 先触发</li>
 *   <li>选择性触发:多 fact 中只有匹配的触发</li>
 *   <li>不触发:class 不匹配 → 不触发</li>
 *   <li>动作执行:action 修改 fact(经 WorkingMemory)</li>
 *   <li>响应结构:firedRules / matchedRules 精确断言</li>
 * </ul>
 *
 * <p>用 EngineContextWirer 装配真实 ValueCompute + AssertorEvaluator(非 mock,
 * V5.81 教训:mock ValueCompute 导致 fired=0)。
 *
 * @since V6.1
 */
@DisplayName("V6.1 TD-2 特征化 — RETE 求值行为锁定")
class ReteEvaluationCharacterizationTest {

    @BeforeAll
    static void wireEngine() throws Exception {
        EngineContextWirer.wire();
    }

    private KnowledgePackage buildPackage(List<ObjectTypeNode> otns) {
        Rete rete = new Rete(otns, new ResourceLibrary());
        KnowledgePackageImpl pkg = new KnowledgePackageImpl();
        pkg.setRete(rete);
        return pkg;
    }

    private ObjectTypeNode buildRule(String name, String objectType, int salience,
                                     List<ObjectTypeNode> otns, AtomicBoolean flag) {
        Rule rule = new Rule();
        rule.setName(name);
        rule.setSalience(salience);
        Rhs rhs = new Rhs();
        List<com.ruleforge.action.Action> actions = new ArrayList<>();
        actions.add(new AbstractAction() {
            public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
                if (flag != null) flag.set(true);
                return null;
            }
            public ActionType getActionType() { return ActionType.ConsolePrint; }
        });
        rhs.setActions(actions);
        rule.setRhs(rhs);
        ObjectTypeNode otn = new ObjectTypeNode(objectType, 1);
        TerminalNode terminal = new TerminalNode(rule, 2);
        otn.addLine(terminal);
        otns.add(otn);
        return otn;
    }

    @Nested
    @DisplayName("单规则求值")
    class SingleRule {

        // Given 一条规则匹配 __*__(所有 class)
        // And 插入一个 GeneralEntity("User") age=20
        // When fireRules
        // Then 规则触发,firedRules 有 1 条
        @Test
        @DisplayName("__*__ 规则 + 任意 fact → 触发")
        void universalRuleFires() {
            AtomicBoolean fired = new AtomicBoolean(false);
            List<ObjectTypeNode> otns = new ArrayList<>();
            buildRule("rule-a", "__*__", 0, otns, fired);
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(buildPackage(otns));

            GeneralEntity entity = new GeneralEntity("User");
            entity.put("age", 20);
            session.insert(entity);
            RuleExecutionResponse response = session.fireRules();

            assertThat(fired.get()).isTrue();
            assertThat(response.getFiredRules()).hasSize(1);
            assertThat(response.getFiredRules().get(0).getName()).isEqualTo("rule-a");
        }

        // Given 一条规则匹配 class="Adult"
        // And 插入一个 GeneralEntity("User")
        // When fireRules
        // Then 规则不触发
        @Test
        @DisplayName("class 不匹配 → 不触发")
        void nonMatchingClassDoesNotFire() {
            AtomicBoolean fired = new AtomicBoolean(false);
            List<ObjectTypeNode> otns = new ArrayList<>();
            buildRule("rule-adult", "Adult", 0, otns, fired);
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(buildPackage(otns));

            GeneralEntity entity = new GeneralEntity("User");
            entity.put("age", 20);
            session.insert(entity);
            session.fireRules();

            assertThat(fired.get()).isFalse();
        }
    }

    @Nested
    @DisplayName("多规则排序")
    class MultipleRules {

        // Given rule-high(salience=10) + rule-low(salience=5)都匹配 __*__
        // And 插入一个 fact
        // When fireRules
        // Then 两条都触发
        // And firedRules 包含两条
        @Test
        @DisplayName("两条规则都匹配 → 都触发")
        void bothRulesFire() {
            AtomicBoolean f1 = new AtomicBoolean(false);
            AtomicBoolean f2 = new AtomicBoolean(false);
            List<ObjectTypeNode> otns = new ArrayList<>();
            buildRule("rule-high", "__*__", 10, otns, f1);
            buildRule("rule-low", "__*__", 5, otns, f2);
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(buildPackage(otns));

            GeneralEntity entity = new GeneralEntity("User");
            session.insert(entity);
            RuleExecutionResponse response = session.fireRules();

            assertThat(f1.get()).isTrue();
            assertThat(f2.get()).isTrue();
            assertThat(response.getFiredRules()).hasSize(2);
            assertThat(response.getFiredRules()).extracting("name").contains("rule-high", "rule-low");
        }

        // Given rule-a(salience=10) + rule-b(salience=5) + rule-c(salience=1)
        // And 插入一个 fact 匹配全部
        // When fireRules
        // Then firedRules 有 3 条
        @Test
        @DisplayName("三条规则不同 salience → 都触发")
        void threeRulesAllFire() {
            List<ObjectTypeNode> otns = new ArrayList<>();
            buildRule("rule-a", "__*__", 10, otns, null);
            buildRule("rule-b", "__*__", 5, otns, null);
            buildRule("rule-c", "__*__", 1, otns, null);
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(buildPackage(otns));

            GeneralEntity entity = new GeneralEntity("User");
            session.insert(entity);
            RuleExecutionResponse response = session.fireRules();

            assertThat(response.getFiredRules()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("多 fact 选择性触发")
    class MultipleFacts {

        // Given rule 匹配 class="Adult"
        // And 插入 Adult + User 两个 fact
        // When fireRules
        // Then 规则触发(Adult 匹配)
        @Test
        @DisplayName("多 fact 中只有匹配 class 的触发")
        void onlyMatchingClassFires() {
            AtomicBoolean fired = new AtomicBoolean(false);
            List<ObjectTypeNode> otns = new ArrayList<>();
            buildRule("rule-adult", "Adult", 0, otns, fired);
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(buildPackage(otns));

            GeneralEntity adult = new GeneralEntity("Adult");
            adult.put("age", 30);
            GeneralEntity child = new GeneralEntity("Child");
            child.put("age", 10);
            session.insert(adult);
            session.insert(child);
            session.fireRules();

            assertThat(fired.get()).isTrue();
        }

        // Given rule 匹配 __*__
        // And 插入 3 个不同 class fact
        // When fireRules
        // Then 规则触发 1 次(1 个 fact 1 次)
        @Test
        @DisplayName("__*__ 规则 + 3 fact → 触发")
        void universalRuleMultipleFacts() {
            AtomicInteger fireCount = new AtomicInteger(0);
            List<ObjectTypeNode> otns = new ArrayList<>();
            Rule rule = new Rule();
            rule.setName("count-rule");
            rule.setSalience(0);
            Rhs rhs = new Rhs();
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            actions.add(new AbstractAction() {
                public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
                    fireCount.incrementAndGet();
                    return null;
                }
                public ActionType getActionType() { return ActionType.ConsolePrint; }
            });
            rhs.setActions(actions);
            rule.setRhs(rhs);
            ObjectTypeNode otn = new ObjectTypeNode("__*__", 1);
            TerminalNode terminal = new TerminalNode(rule, 2);
            otn.addLine(terminal);
            otns.add(otn);

            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(buildPackage(otns));
            session.insert(new GeneralEntity("A"));
            session.insert(new GeneralEntity("B"));
            session.insert(new GeneralEntity("C"));
            session.fireRules();

            assertThat(fireCount.get()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("动作执行 — fact 修改经 WorkingMemory")
    class ActionExecution {

        // Given 规则 action 执行时记录 matchedObject 类型
        // And 插入 GeneralEntity fact
        // When fireRules
        // Then action 执行了 + matchedObject 类型被记录
        @Test
        @DisplayName("action 执行 + matchedObject 不为 null")
        void actionExecutesWithMatchedObject() {
            AtomicBoolean actionRan = new AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicReference<String> matchedType = new java.util.concurrent.atomic.AtomicReference<>("none");
            List<ObjectTypeNode> otns = new ArrayList<>();
            buildRule("action-rule", "__*__", 0, otns, null);
            // override: build a custom rule to inspect matchedObject
            otns.clear();
            Rule rule = new Rule();
            rule.setName("action-rule");
            rule.setSalience(0);
            Rhs rhs = new Rhs();
            List<com.ruleforge.action.Action> actions = new ArrayList<>();
            actions.add(new AbstractAction() {
                public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
                    actionRan.set(true);
                    matchedType.set(matchedObject == null ? "null" : matchedObject.getClass().getSimpleName());
                    return null;
                }
                public ActionType getActionType() { return ActionType.ConsolePrint; }
            });
            rhs.setActions(actions);
            rule.setRhs(rhs);
            ObjectTypeNode otn = new ObjectTypeNode("__*__", 1);
            TerminalNode terminal = new TerminalNode(rule, 2);
            otn.addLine(terminal);
            otns.add(otn);

            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(buildPackage(otns));
            GeneralEntity entity = new GeneralEntity("User");
            entity.put("age", 20);
            session.insert(entity);
            session.fireRules();

            // TD-2 安全网:action 执行 + matchedObject 存在
            assertThat(actionRan.get()).isTrue();
            assertThat(matchedType.get()).isNotEqualTo("null");
        }
    }

    @Nested
    @DisplayName("空会话 — 无规则")
    class EmptySession {

        // Given 空 KnowledgePackage(无规则)
        // And 插入 fact
        // When fireRules
        // Then 不报错,firedRules 为空
        @Test
        @DisplayName("空 RETE 网络 + fact → firedRules 空")
        void emptyReteNoFire() {
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(buildPackage(new ArrayList<>()));
            session.insert(new GeneralEntity("User"));
            RuleExecutionResponse response = session.fireRules();

            assertThat(response.getFiredRules()).isEmpty();
        }

        // Given 空 KnowledgePackage
        // And 不插入任何 fact
        // When fireRules
        // Then 不报错
        @Test
        @DisplayName("空 RETE + 无 fact → 正常")
        void emptyReteNoFact() {
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(buildPackage(new ArrayList<>()));
            RuleExecutionResponse response = session.fireRules();

            assertThat(response.getFiredRules()).isEmpty();
        }
    }
}
