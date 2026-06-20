package com.ruleforge.model.rule;

import com.ruleforge.action.Action;
import com.ruleforge.action.ActionType;
import com.ruleforge.action.ActionValue;
import com.ruleforge.engine.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.9.5 — {@link ElseRuleBuilder#buildElseRule} 行为契约 BDD。
 *
 * <p>锁 V6.9.5 收口 (3-level nested if/else state machine → early return chain +
 * {@code !isEmpty()} 风格统一) 的行为不变性:
 * <ul>
 *   <li><b>已有 elseRule</b>: 返已缓存的 elseRule (memoization)</li>
 *   <li><b>other == null</b>: 返 null</li>
 *   <li><b>other.actions 空</b>: 返 null</li>
 *   <li><b>other.actions 非空</b>: 派生新 Rule,继承原 rule 元数据,RHS 复用 other.actions</li>
 *   <li><b>派生后 rule.getElseRule() == 新 Rule</b> (memoization)</li>
 * </ul>
 *
 * <p><b>Why V6.9.5 选这条</b>: 跟 V6.2-V6.4-V6.9.2-V6.9.3-V6.9.4 同档 Fernflower 反编译
 * state machine 收口。{@code ElseRuleBuilder.buildElseRule} L15-41 旧实现是 3-level nested
 * if/else:
 * <pre>
 *   if (rule.getElseRule() != null) return rule.getElseRule();
 *   else {
 *       if (other != null && other.getActions().size() != 0) { ... }
 *       else { return null; }
 *   }
 * </pre>
 * 收口成 early return chain + size()!=0 → !isEmpty(),行为 100% 等价。
 */
@DisplayName("V6.9.5 — ElseRuleBuilder.buildElseRule 行为契约")
class ElseRuleBuilderTest {

    private Rule rule;

    @BeforeEach
    void setUp() {
        rule = new Rule();
        rule.setName("rule1");
        rule.setSalience(10);
        rule.setActivationGroup("group1");
        rule.setAgendaGroup("agenda1");
        rule.setAutoFocus(true);
    }

    private static Action action() {
        return new Action() {
            @Override
            public ActionValue execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
                return null;
            }

            @Override
            public ActionType getActionType() {
                return ActionType.ConsolePrint;
            }

            @Override
            public int getPriority() {
                return 0;
            }

            @Override
            public int compareTo(Action other) {
                return 0;
            }

            @Override
            public void setDebug(boolean debug) {
            }
        };
    }

    @Nested
    @DisplayName("已有 elseRule → 返缓存 (memoization)")
    class CachedElseRule {

        // Given: rule.getElseRule() != null (已缓存)
        // When:  buildElseRule(rule)
        // Then:  返 rule.getElseRule() (同一 instance,不重新派生)
        @Test
        @DisplayName("rule.getElseRule() != null → 返缓存,不重新派生")
        void returnsCachedElseRule() {
            Rule cached = new Rule();
            cached.setName("cached");
            rule.setElseRule(cached);

            assertThat(ElseRuleBuilder.buildElseRule(rule)).isSameAs(cached);
        }
    }

    @Nested
    @DisplayName("rule.getOther() == null → 返 null")
    class NullOther {

        // Given: rule.getOther() == null
        // When:  buildElseRule(rule)
        // Then:  返 null
        @Test
        @DisplayName("other == null → 返 null")
        void nullOtherReturnsNull() {
            assertThat(ElseRuleBuilder.buildElseRule(rule)).isNull();
        }
    }

    @Nested
    @DisplayName("other.actions 空 → 返 null")
    class EmptyActions {

        // Given: rule.getOther() != null, actions == null (V6.9.5 — 修 pre-existing NPE,
        // 收口 state machine 顺手 null safety,跟 V6.9.2 AndActivity/OrActivity 同档)
        // When:  buildElseRule(rule)
        // Then:  返 null (旧实现 NPE)
        @Test
        @DisplayName("actions == null → 返 null (V6.9.5 — 修 pre-existing NPE)")
        void nullActionsReturnsNull() {
            Other other = new Other();
            // actions 永为 null (未 addAction)
            rule.setOther(other);
            assertThat(ElseRuleBuilder.buildElseRule(rule)).isNull();
        }

        @Test
        @DisplayName("actions.isEmpty() → 返 null")
        void emptyActionsReturnsNull() {
            Other other = new Other();
            other.setActions(Collections.emptyList());
            rule.setOther(other);
            assertThat(ElseRuleBuilder.buildElseRule(rule)).isNull();
        }
    }

    @Nested
    @DisplayName("other.actions 非空 → 派生新 Rule")
    class DeriveElseRule {

        // Given: rule 元数据 + Other with 1+ actions
        // When:  buildElseRule(rule)
        // Then:  返新 Rule, name = rule.name + "else", salience/activationGroup/agendaGroup 继承
        @Test
        @DisplayName("派生 elseRule: name = rule.name + 'else'")
        void derivesElseRuleWithNameSuffix() {
            Other other = new Other();
            other.setActions(Collections.singletonList(action()));
            rule.setOther(other);

            Rule elseRule = ElseRuleBuilder.buildElseRule(rule);

            assertThat(elseRule).isNotNull();
            assertThat(elseRule.getName()).isEqualTo("rule1else");
        }

        @Test
        @DisplayName("派生 elseRule: 元数据继承 (salience/activationGroup/agendaGroup/autoFocus)")
        void derivesElseRuleInheritsMetadata() {
            Other other = new Other();
            other.setActions(Collections.singletonList(action()));
            rule.setOther(other);

            Rule elseRule = ElseRuleBuilder.buildElseRule(rule);

            assertThat(elseRule).isNotNull();
            assertThat(elseRule.getSalience()).isEqualTo(10);
            assertThat(elseRule.getActivationGroup()).isEqualTo("group1");
            assertThat(elseRule.getAgendaGroup()).isEqualTo("agenda1");
            assertThat(elseRule.getAutoFocus()).isTrue();
        }

        @Test
        @DisplayName("派生 elseRule: RHS.actions 复用 other.actions")
        void derivesElseRuleSharesActions() {
            Action a1 = action();
            Action a2 = action();
            Other other = new Other();
            other.setActions(java.util.Arrays.asList(a1, a2));
            rule.setOther(other);

            Rule elseRule = ElseRuleBuilder.buildElseRule(rule);

            assertThat(elseRule).isNotNull();
            assertThat(elseRule.getRhs()).isNotNull();
            assertThat(elseRule.getRhs().getActions()).containsExactly(a1, a2);
        }

        @Test
        @DisplayName("派生 elseRule: rule.elseRule 缓存新 Rule (memoization)")
        void derivedElseRuleIsCached() {
            Other other = new Other();
            other.setActions(Collections.singletonList(action()));
            rule.setOther(other);

            Rule first = ElseRuleBuilder.buildElseRule(rule);
            Rule second = ElseRuleBuilder.buildElseRule(rule);

            assertThat(second).isSameAs(first);
        }
    }
}
