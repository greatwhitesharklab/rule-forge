package com.ruleforge.v1.exec;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.v1.ast.Action;
import com.ruleforge.v1.ast.ActionType;
import com.ruleforge.v1.ast.HitPolicy;
import com.ruleforge.v1.ast.RuleSetNode;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.SchemaField;
import com.ruleforge.v1.ast.V1DataType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V7.0.0 W2-1 — RuleSet 编译器 BDD。
 *
 * <p>RuleSetNode → RETE rules → KnowledgePackage → insert GeneralEntity fact → fire →
 * 验证 hitPolicy + actions 正确。这是 V1 fact 模型(GeneralEntity/Map)+ buildRete 的
 * 首次端到端集成验证(W2 关键)。
 */
@DisplayName("V7.0.0 W2-1 — RuleSet 编译器")
class RuleSetCompilerTest {

    private static Schema loanSchema() {
        Schema s = new Schema();
        s.setName("LoanApplication");
        s.setFields(Arrays.asList(
                new SchemaField("age", V1DataType.NUMBER),
                new SchemaField("blacklisted", V1DataType.BOOLEAN),
                new SchemaField("decision", V1DataType.STRING)));
        return s;
    }

    private static RuleSetNode precheckRuleSet() {
        // 准入:黑名单 → REJECT,未成年 → REJECT(FIRST_MATCH)
        RuleSetNode node = new RuleSetNode();
        node.setId("precheck");
        node.setName("准入规则");
        node.setHitPolicy(HitPolicy.FIRST_MATCH);
        Action reject1 = new Action(ActionType.REJECT);
        reject1.setReason("BLACKLIST");
        Action reject2 = new Action(ActionType.REJECT);
        reject2.setReason("UNDERAGE");
        com.ruleforge.v1.ast.Rule r1 = new com.ruleforge.v1.ast.Rule();
        r1.setId("blacklist");
        r1.setCondition("blacklisted == true");
        r1.setActions(Collections.singletonList(reject1));
        com.ruleforge.v1.ast.Rule r2 = new com.ruleforge.v1.ast.Rule();
        r2.setId("underage");
        r2.setCondition("age < 18");
        r2.setActions(Collections.singletonList(reject2));
        node.setRules(Arrays.asList(r1, r2));
        return node;
    }

    /** 编译 + 建 session + insert fact + fire。 */
    private Map<String, Object> fire(RuleSetNode node, Schema schema, Map<String, Object> fact) {
        List<Rule> rules = RuleSetCompiler.compile(node, schema);
        KnowledgePackage kp = V1KnowledgeBuilder.build(schema, rules);
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        session.insert(fact);
        session.fireRules();
        return fact;
    }

    @BeforeAll
    static void wire() throws Exception {
        EngineContextWirer.wire();
    }

    @Test
    @DisplayName("黑名单命中 → REJECT(decision=reject + _rejected=true)")
    void 黑名单命中_REJECT() {
        // Given precheck RuleSet + blacklisted=true fact When fire Then decision=reject + rejected
        Map<String, Object> fact = new GeneralEntity("LoanApplication");
        fact.put("blacklisted", true);
        fact.put("age", 30);
        fire(precheckRuleSet(), loanSchema(), fact);
        assertThat(fact.get("decision")).isEqualTo("reject");
        assertThat(fact.get(V1ActionRhs.REJECTED_FLAG)).isEqualTo(true);
        assertThat(fact.get(V1ActionRhs.REJECT_REASON)).isEqualTo("BLACKLIST");
    }

    @Test
    @DisplayName("未成年命中 → REJECT UNDERAGE")
    void 未成年命中_REJECT_UNDERAGE() {
        Map<String, Object> fact = new GeneralEntity("LoanApplication");
        fact.put("blacklisted", false);
        fact.put("age", 16);
        fire(precheckRuleSet(), loanSchema(), fact);
        assertThat(fact.get("decision")).isEqualTo("reject");
        assertThat(fact.get(V1ActionRhs.REJECT_REASON)).isEqualTo("UNDERAGE");
    }

    @Test
    @DisplayName("都不命中 → 不 reject(decision 未设)")
    void 都不命中_不_reject() {
        Map<String, Object> fact = new GeneralEntity("LoanApplication");
        fact.put("blacklisted", false);
        fact.put("age", 30);
        fire(precheckRuleSet(), loanSchema(), fact);
        assertThat(fact.containsKey(V1ActionRhs.REJECTED_FLAG)).isFalse();
    }

    @Test
    @DisplayName("extractUnconditionalRules → 空 condition 与 'true' condition 按序返回,排除 disabled")
    void extractUnconditionalRules_空condition与true_按序返回_排除disabled() {
        // Given RuleSet 4 条:空 cond + "true" cond + disabled 空 cond + 条件 cond
        RuleSetNode node = new RuleSetNode();
        node.setId("mix");
        node.setHitPolicy(HitPolicy.ALL_MATCH);
        com.ruleforge.v1.ast.Rule rUnconditional = new com.ruleforge.v1.ast.Rule();
        rUnconditional.setId("base"); rUnconditional.setCondition(""); rUnconditional.setActions(Collections.emptyList());
        com.ruleforge.v1.ast.Rule rTrue = new com.ruleforge.v1.ast.Rule();
        rTrue.setId("alwaystrue"); rTrue.setCondition("true"); rTrue.setActions(Collections.emptyList());
        com.ruleforge.v1.ast.Rule rDisabled = new com.ruleforge.v1.ast.Rule();
        rDisabled.setId("disabled"); rDisabled.setCondition(""); rDisabled.setEnabled(false); rDisabled.setActions(Collections.emptyList());
        com.ruleforge.v1.ast.Rule rConditional = new com.ruleforge.v1.ast.Rule();
        rConditional.setId("cond"); rConditional.setCondition("age >= 18"); rConditional.setActions(Collections.emptyList());
        node.setRules(Arrays.asList(rUnconditional, rTrue, rDisabled, rConditional));
        // When extract unconditional
        List<com.ruleforge.v1.ast.Rule> unconditional = RuleSetCompiler.extractUnconditionalRules(node);
        // Then 返回前两条原序(空 cond + true cond),disabled 与条件规则不在内
        assertThat(unconditional).hasSize(2);
        assertThat(unconditional.get(0).getId()).isEqualTo("base");
        assertThat(unconditional.get(1).getId()).isEqualTo("alwaystrue");
    }

    @Test
    @DisplayName("ALL_MATCH 多条命中 → actions 全执行(SET_VARIABLE)")
    void ALL_MATCH_多条命中_全执行() {
        // Given RuleSet ALL_MATCH 2 条 SET_VARIABLE + 都命中 When fire Then 两字段都设
        RuleSetNode node = new RuleSetNode();
        node.setId("ops");
        node.setHitPolicy(HitPolicy.ALL_MATCH);
        Action a1 = new Action(ActionType.SET_VARIABLE);
        a1.setTarget("decision"); a1.setValue("flagged");
        Action a2 = new Action(ActionType.FLAG);
        a2.setReason("HIGH_RISK");
        com.ruleforge.v1.ast.Rule r1 = new com.ruleforge.v1.ast.Rule();
        r1.setCondition("age >= 18"); r1.setActions(Collections.singletonList(a1));
        com.ruleforge.v1.ast.Rule r2 = new com.ruleforge.v1.ast.Rule();
        r2.setCondition("age >= 18"); r2.setActions(Collections.singletonList(a2));
        node.setRules(Arrays.asList(r1, r2));

        Map<String, Object> fact = new GeneralEntity("LoanApplication");
        fact.put("age", 30);
        fire(node, loanSchema(), fact);
        assertThat(fact.get("decision")).isEqualTo("flagged");
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<Object> flags = (List) fact.get("flags");
        assertThat(flags).contains("HIGH_RISK");
    }
}
