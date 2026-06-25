package com.ruleforge.v1.exec;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.v1.ast.Action;
import com.ruleforge.v1.ast.ActionType;
import com.ruleforge.v1.ast.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.0.0 W1-4 — V1 Action → RETE RHS 翻译 + 执行 BDD。
 *
 * <p>直接单测每个 action 的 execute(Map fact) — 隔离 fact 模型/RETE 匹配问题(留 W2),
 * 聚焦 W1-4 交付物:5 种结构化 action 翻译成 RETE action 后对 Map fact 的副作用正确。
 *
 * <p>fact 用 GeneralEntity(Map-backed,V1 真实 fact 模型)。
 */
@DisplayName("V7.0.0 W1-4 — V1 Action → RETE RHS 翻译 + 执行")
class V1ActionRhsTest {

    private Schema emptySchema() {
        Schema s = new Schema();
        s.setName("LoanApplication");
        return s;
    }

    /** 执行一个 V1 action 列表 against fact,返回执行后的 fact。 */
    private Map<String, Object> exec(List<Action> v1Actions, Map<String, Object> fact) {
        List<com.ruleforge.action.Action> reteActions =
                V1ActionRhs.translate(v1Actions, emptySchema());
        for (com.ruleforge.action.Action a : reteActions) {
            a.execute(null, fact, Collections.emptyList());
        }
        return fact;
    }

    @Nested
    @DisplayName("SET_VARIABLE")
    class SetVariable {
        @Test
        void 字面量_写入_target字段() {
            // Given SET_VARIABLE target=rate value=0.18 When exec Then fact[rate]=0.18
            Action a = new Action(ActionType.SET_VARIABLE);
            a.setTarget("rate");
            a.setValue(0.18);
            Map<String, Object> fact = new GeneralEntity("LoanApplication");
            exec(Collections.singletonList(a), fact);
            assertThat(fact.get("rate")).isEqualTo(0.18);
        }

        @Test
        void ref_字段引用_复制另一字段值() {
            // Given SET_VARIABLE target=baseRate ref=rate When exec(rate=0.2) Then baseRate=0.2
            Action a = new Action(ActionType.SET_VARIABLE);
            a.setTarget("baseRate");
            a.setRef("rate");
            Map<String, Object> fact = new GeneralEntity("LoanApplication");
            fact.put("rate", 0.2);
            exec(Collections.singletonList(a), fact);
            assertThat(fact.get("baseRate")).isEqualTo(0.2);
        }
    }

    @Nested
    @DisplayName("ADD_SCORE")
    class AddScore {
        @Test
        void 累加_到_空字段_从_0_起() {
            // Given ADD_SCORE target=marketingScore value=20 When exec(无 marketingScore) Then 20
            Action a = new Action(ActionType.ADD_SCORE);
            a.setTarget("marketingScore");
            a.setValue(20);
            Map<String, Object> fact = new GeneralEntity("LoanApplication");
            exec(Collections.singletonList(a), fact);
            assertThat(((Number) fact.get("marketingScore")).doubleValue()).isEqualTo(20.0);
        }

        @Test
        void 累加_到_已有值() {
            // Given ADD_SCORE target=marketingScore value=30 When exec(已有 20) Then 50
            Action a = new Action(ActionType.ADD_SCORE);
            a.setTarget("marketingScore");
            a.setValue(30);
            Map<String, Object> fact = new GeneralEntity("LoanApplication");
            fact.put("marketingScore", 20);
            exec(Collections.singletonList(a), fact);
            assertThat(((Number) fact.get("marketingScore")).doubleValue()).isEqualTo(50.0);
        }
    }

    @Nested
    @DisplayName("SET_DECISION")
    class SetDecision {
        @Test
        void 写入_decision_字段() {
            // Given SET_DECISION value=approve When exec Then fact[decision]=approve
            Action a = new Action(ActionType.SET_DECISION);
            a.setValue("approve");
            Map<String, Object> fact = new GeneralEntity("LoanApplication");
            exec(Collections.singletonList(a), fact);
            assertThat(fact.get("decision")).isEqualTo("approve");
        }
    }

    @Nested
    @DisplayName("REJECT")
    class Reject {
        @Test
        void 设_decision_reject_加_rejected_标记_reason() {
            // Given REJECT reason=BLACKLIST When exec Then decision=reject + _rejected=true + reason
            Action a = new Action(ActionType.REJECT);
            a.setReason("BLACKLIST");
            Map<String, Object> fact = new GeneralEntity("LoanApplication");
            exec(Collections.singletonList(a), fact);
            assertThat(fact.get("decision")).isEqualTo("reject");
            assertThat(fact.get(V1ActionRhs.REJECTED_FLAG)).isEqualTo(true);
            assertThat(fact.get(V1ActionRhs.REJECT_REASON)).isEqualTo("BLACKLIST");
        }
    }

    @Nested
    @DisplayName("FLAG")
    class Flag {
        @Test
        void 追加_reason_到_flags_列表_无列表新建() {
            // Given FLAG reason=DEVICE_FRAUD When exec(无 flags) Then flags=[DEVICE_FRAUD]
            Action a = new Action(ActionType.FLAG);
            a.setReason("DEVICE_FRAUD");
            Map<String, Object> fact = new GeneralEntity("LoanApplication");
            exec(Collections.singletonList(a), fact);
            @SuppressWarnings({"rawtypes","unchecked"}) java.util.List<Object> _f = (java.util.List) fact.get("flags"); assertThat(_f).containsExactly("DEVICE_FRAUD");
        }

        @Test
        void 追加_到_已有_flags_列表() {
            // Given FLAG reason=B When exec(flags=[A]) Then flags=[A,B]
            Action a = new Action(ActionType.FLAG);
            a.setReason("B");
            Map<String, Object> fact = new GeneralEntity("LoanApplication");
            fact.put("flags", new java.util.ArrayList<>(Collections.singletonList("A")));
            exec(Collections.singletonList(a), fact);
            @SuppressWarnings({"rawtypes","unchecked"}) java.util.List<Object> _f = (java.util.List) fact.get("flags"); assertThat(_f).containsExactly("A", "B");
        }
    }

    @Test
    @DisplayName("translate 空 list → 空 rete action list")
    void 空_actions_返回空列表() {
        assertThat(V1ActionRhs.translate(null, emptySchema())).isEmpty();
        assertThat(V1ActionRhs.translate(Collections.emptyList(), emptySchema())).isEmpty();
    }
}
