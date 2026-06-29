package com.ruleforge.v1.exec;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.v1.ast.Action;
import com.ruleforge.v1.ast.ActionType;
import com.ruleforge.v1.ast.HitPolicy;
import com.ruleforge.v1.ast.RuleSetNode;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.SchemaField;
import com.ruleforge.v1.ast.V1DataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.8 — RuleSet 无条件 / catch-all 规则执行器 BDD。
 *
 * <p>验证 {@link RuleSetExecutor} 按 hitPolicy 正确应用无条件规则(空 condition / {@code "true"}):
 * <ul>
 *   <li>FIRST_MATCH / PRIORITY — catch-all / else 语义:仅当无条件规则未命中且未 reject 时应用</li>
 *   <li>ALL_MATCH — 始终应用(base/setup 先,条件规则可覆盖)</li>
 *   <li>reject 优先于 catch-all</li>
 *   <li>仅无条件规则也触发</li>
 * </ul>
 *
 * <p>V7.8 修 V7.0 遗留:{@code RuleSetCompiler} 静默丢弃空 condition 规则的 bug。
 */
@DisplayName("V7.8 — RuleSet 无条件 / catch-all 规则")
class RuleSetExecutorTest {

    private static Schema loanSchema() {
        Schema s = new Schema();
        s.setName("LoanApplication");
        s.setFields(Arrays.asList(
                new SchemaField("age", V1DataType.NUMBER),
                new SchemaField("blacklisted", V1DataType.BOOLEAN),
                new SchemaField("vip", V1DataType.BOOLEAN),
                new SchemaField("decision", V1DataType.STRING),
                new SchemaField("rate", V1DataType.NUMBER),
                new SchemaField("score", V1DataType.NUMBER)));
        return s;
    }

    /** 准入 RuleSet(FIRST_MATCH):blacklisted→reject + 未成年→reject + 空 cond catch-all→SET_DECISION review。 */
    private static RuleSetNode precheckWithCatchAll() {
        RuleSetNode node = new RuleSetNode();
        node.setId("precheck");
        node.setHitPolicy(HitPolicy.FIRST_MATCH);
        Action reject1 = new Action(ActionType.REJECT);
        reject1.setReason("BLACKLIST");
        Action reject2 = new Action(ActionType.REJECT);
        reject2.setReason("UNDERAGE");
        Action defaultReview = new Action(ActionType.SET_DECISION);
        defaultReview.setValue("review");
        com.ruleforge.v1.ast.Rule r1 = new com.ruleforge.v1.ast.Rule();
        r1.setId("blacklist"); r1.setCondition("blacklisted == true"); r1.setActions(Collections.singletonList(reject1));
        com.ruleforge.v1.ast.Rule r2 = new com.ruleforge.v1.ast.Rule();
        r2.setId("underage"); r2.setCondition("age < 18"); r2.setActions(Collections.singletonList(reject2));
        com.ruleforge.v1.ast.Rule rCatchAll = new com.ruleforge.v1.ast.Rule();
        rCatchAll.setId("default"); rCatchAll.setCondition(""); rCatchAll.setActions(Collections.singletonList(defaultReview));
        node.setRules(Arrays.asList(r1, r2, rCatchAll));
        return node;
    }

    private static Map<String, Object> fact(Object... kv) {
        Map<String, Object> f = new GeneralEntity("LoanApplication");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            f.put((String) kv[i], kv[i + 1]);
        }
        return f;
    }

    private static void run(RuleSetNode node, Map<String, Object> fact) {
        RuleSetExecutor.execute(node, loanSchema(), fact, null);
    }

    @BeforeAll
    static void wire() throws Exception {
        EngineContextWirer.wire();
    }

    @Nested
    @DisplayName("FIRST_MATCH catch-all / else 语义")
    class FirstMatchCatchAll {

        @Test
        @DisplayName("条件全未命中 → 应用 catch-all(decision=review,未 reject)")
        void 条件全未命中_应用catchall() {
            // Given precheck RuleSet + catch-all When blacklisted=false age=30 Then decision=review not rejected
            Map<String, Object> f = fact("blacklisted", false, "age", 30);
            run(precheckWithCatchAll(), f);
            assertThat(f.get("decision")).isEqualTo("review");
            assertThat(f.containsKey(V1ActionRhs.REJECTED_FLAG)).isFalse();
        }

        @Test
        @DisplayName("条件命中(非 reject) → 跳过 catch-all(decision=approve,不是 review)")
        void 条件命中_跳过catchall() {
            // Given RuleSet: age>=18→SET_DECISION approve + 空 cond catch-all→review When age=30 Then approve
            RuleSetNode node = new RuleSetNode();
            node.setId("approve"); node.setHitPolicy(HitPolicy.FIRST_MATCH);
            Action approve = new Action(ActionType.SET_DECISION); approve.setValue("approve");
            Action dflt = new Action(ActionType.SET_DECISION); dflt.setValue("review");
            com.ruleforge.v1.ast.Rule r1 = new com.ruleforge.v1.ast.Rule();
            r1.setId("adult"); r1.setCondition("age >= 18"); r1.setActions(Collections.singletonList(approve));
            com.ruleforge.v1.ast.Rule rCatchAll = new com.ruleforge.v1.ast.Rule();
            rCatchAll.setId("default"); rCatchAll.setCondition(""); rCatchAll.setActions(Collections.singletonList(dflt));
            node.setRules(Arrays.asList(r1, rCatchAll));

            Map<String, Object> f = fact("age", 30);
            run(node, f);
            assertThat(f.get("decision")).isEqualTo("approve");
            assertThat(f.containsKey(V1ActionRhs.REJECTED_FLAG)).isFalse();
        }

        @Test
        @DisplayName("条件 reject → 跳过 catch-all(reject 优先,decision=reject 不是 review)")
        void 条件reject_跳过catchall() {
            // Given precheck RuleSet When blacklisted=true Then reject 优先,catch-all 未覆盖
            Map<String, Object> f = fact("blacklisted", true, "age", 30);
            run(precheckWithCatchAll(), f);
            assertThat(f.get("decision")).isEqualTo("reject");
            assertThat(f.get(V1ActionRhs.REJECTED_FLAG)).isEqualTo(true);
            assertThat(f.get(V1ActionRhs.REJECT_REASON)).isEqualTo("BLACKLIST");
        }
    }

    @Nested
    @DisplayName("ALL_MATCH 始终应用(base 先,条件可覆盖)")
    class AllMatchAlwaysApply {

        @Test
        @DisplayName("无条件 base 先应用,条件命中可覆盖(vip=true→rate=0.12;vip=false→rate=0.18)")
        void 无条件base先应用_条件可覆盖() {
            // Given ALL_MATCH: 空 cond SET rate=0.18 + vip==true SET rate=0.12 When vip true/false Then 覆盖/base
            RuleSetNode node = new RuleSetNode();
            node.setId("rate"); node.setHitPolicy(HitPolicy.ALL_MATCH);
            Action base = new Action(ActionType.SET_VARIABLE); base.setTarget("rate"); base.setValue("0.18");
            Action vipRate = new Action(ActionType.SET_VARIABLE); vipRate.setTarget("rate"); vipRate.setValue("0.12");
            com.ruleforge.v1.ast.Rule rBase = new com.ruleforge.v1.ast.Rule();
            rBase.setId("base"); rBase.setCondition(""); rBase.setActions(Collections.singletonList(base));
            com.ruleforge.v1.ast.Rule rVip = new com.ruleforge.v1.ast.Rule();
            rVip.setId("vip"); rVip.setCondition("vip == true"); rVip.setActions(Collections.singletonList(vipRate));
            node.setRules(Arrays.asList(rBase, rVip));

            Map<String, Object> fVip = fact("vip", true);
            run(node, fVip);
            assertThat(fVip.get("rate")).isEqualTo("0.12");

            Map<String, Object> fNonVip = fact("vip", false);
            run(node, fNonVip);
            assertThat(fNonVip.get("rate")).isEqualTo("0.18");
        }

        @Test
        @DisplayName("无条件 ADD_SCORE base 累加 + 条件 ADD_SCORE → score=70")
        void 无条件ADD_SCORE_base累加() {
            // Given ALL_MATCH: 空 cond ADD_SCORE score+=20 + vip==true ADD_SCORE score+=50 When vip=true Then 70
            RuleSetNode node = new RuleSetNode();
            node.setId("score"); node.setHitPolicy(HitPolicy.ALL_MATCH);
            Action base = new Action(ActionType.ADD_SCORE); base.setTarget("score"); base.setValue(20);
            Action bonus = new Action(ActionType.ADD_SCORE); bonus.setTarget("score"); bonus.setValue(50);
            com.ruleforge.v1.ast.Rule rBase = new com.ruleforge.v1.ast.Rule();
            rBase.setId("base"); rBase.setCondition(""); rBase.setActions(Collections.singletonList(base));
            com.ruleforge.v1.ast.Rule rBonus = new com.ruleforge.v1.ast.Rule();
            rBonus.setId("bonus"); rBonus.setCondition("vip == true"); rBonus.setActions(Collections.singletonList(bonus));
            node.setRules(Arrays.asList(rBase, rBonus));

            Map<String, Object> f = fact("vip", true);
            run(node, f);
            assertThat(((Number) f.get("score")).doubleValue()).isEqualTo(70.0);
        }
    }

    @Nested
    @DisplayName("仅无条件规则")
    class UnconditionalOnly {

        @Test
        @DisplayName("RuleSet 只有一条空 cond 规则 → 仍触发(不静默丢弃)")
        void 仅无条件规则_也触发() {
            // Given FIRST_MATCH 只有一条空 cond SET_DECISION review When fire Then 应用
            RuleSetNode node = new RuleSetNode();
            node.setId("only"); node.setHitPolicy(HitPolicy.FIRST_MATCH);
            Action dflt = new Action(ActionType.SET_DECISION); dflt.setValue("review");
            com.ruleforge.v1.ast.Rule rCatchAll = new com.ruleforge.v1.ast.Rule();
            rCatchAll.setId("default"); rCatchAll.setCondition(""); rCatchAll.setActions(Collections.singletonList(dflt));
            node.setRules(Collections.singletonList(rCatchAll));

            Map<String, Object> f = fact("age", 30);
            run(node, f);
            assertThat(f.get("decision")).isEqualTo("review");
        }
    }
}