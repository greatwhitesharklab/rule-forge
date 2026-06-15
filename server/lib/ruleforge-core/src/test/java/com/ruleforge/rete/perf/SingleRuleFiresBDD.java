package com.ruleforge.rete.perf;

import com.ruleforge.rete.test.EngineContextWirer;

import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Act;
import com.ruleforge.model.library.variable.CategoryType;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.library.variable.VariableLibrary;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.builder.ReteBuilder;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.81 — 最小 hand-built Rule fired=1 契约 BDD。
 *
 * <p>锁 V5.79 perf bench fired=0 跟 V5.78 DRL 漏填是 2 个独立 bug(见
 * [[v580-drl-regression-fix]] TD-18.4)。本类 1 rule / 1 pattern / 1 fact 最小
 * 场景,期望 firedRules=1。
 *
 * <p>V5.81 修法:用 {@link EngineContextWirer}(真实 ValueCompute)代替 Mockito mock —
 * mock 没 stub {@code complexValueCompute},默认返 null,导致
 * {@code criteria.evaluate} 的 right side 永远是 null,equals(null) 永不命中,所有
 * rule 都不 fire。这是 test 装配套路 bug,不是 production 代码 regression。
 *
 * @see EvalBenchmarkV579 V5.79 perf bench(已切到 EngineContextWirer,TD-19.3)
 * @since 5.81
 */
@DisplayName("V5.81 — 最小 hand-built Rule fired=1 契约")
class SingleRuleFiresBDD {

    public static class Foo {
        private String name;
        public Foo() {}
        public Foo(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @Test
    @DisplayName("Given 1 rule Foo(name == \"alice\") + 1 Foo fact \"alice\", When fireRules, Then firedRules=1")
    void singleRuleFires() {
        // Given
        Rule r = new Rule();
        r.setName("R1");
        r.setSalience(0);

        And and = new And();
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory("Foo");
        part.setVariableName("name");
        part.setVariableLabel("name");
        part.setDatatype(Datatype.String);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue();
        sv.setContent("alice");
        c.setValue(sv);
        and.addCriterion(c);

        Lhs lhs = new Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);

        // ResourceLibrary 含 Foo category(clazz=Foo.class.getName())
        VariableLibrary lib = new VariableLibrary();
        VariableCategory cat = new VariableCategory();
        cat.setName("Foo");
        cat.setType(CategoryType.Clazz);
        cat.setClazz(Foo.class.getName());
        Variable v = new Variable();
        v.setName("name");
        v.setLabel("name");
        v.setType(Datatype.String);
        v.setAct(Act.In);
        cat.setVariables(Collections.singletonList(v));
        lib.addVariableCategory(cat);
        ResourceLibrary rl = new ResourceLibrary(
            Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());

        // When
        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
        KnowledgePackage kp = new KnowledgeBase(rete).getKnowledgePackage();
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        session.insert(new Foo("alice"));
        RuleExecutionResponse resp = session.fireRules();

        // Then
        assertNotNull(resp, "fireRules 应能跑通不抛错");
        assertNotNull(resp.getFiredRules(), "firedRules 不应为 null");
        assertEquals(1, resp.getFiredRules().size(),
            "V5.81 期望: 1 rule + 1 fact 匹配 → firedRules=1。"
            + "本 BDD 锁死: V5.79 bench fired=0 跟 V5.78 DRL 漏填是 2 个独立 bug — "
            + "后者 V5.80 TD-18.0 修;前者是 EngineContext 装配 mock 没 stub "
            + "complexValueCompute, V5.81 TD-19.2 用真实 ValueCompute 修。");
    }

    @Test
    @DisplayName("V5.83 — Given 1 rule + 100 Foo(noise) + 1 Foo(\"alice\") insert 在最后,When fireRules,Then firedRules=1")
    void singleRuleHundredNoiseLast() {
        Rule r = new Rule();
        r.setName("R1"); r.setSalience(0);
        And and = new And();
        Criteria c = new Criteria();
        Left left = new Left(); left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory("Foo"); part.setVariableName("name"); part.setVariableLabel("name");
        part.setDatatype(Datatype.String);
        left.setLeftPart(part); c.setLeft(left); c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue(); sv.setContent("alice"); c.setValue(sv);
        and.addCriterion(c);
        Lhs lhs = new Lhs(); lhs.setCriterion(and); r.setLhs(lhs);

        VariableLibrary lib = new VariableLibrary();
        VariableCategory cat = new VariableCategory();
        cat.setName("Foo"); cat.setType(CategoryType.Clazz); cat.setClazz(Foo.class.getName());
        Variable v = new Variable(); v.setName("name"); v.setLabel("name"); v.setType(Datatype.String); v.setAct(Act.In);
        cat.setVariables(Collections.singletonList(v));
        lib.addVariableCategory(cat);
        ResourceLibrary rl = new ResourceLibrary(Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());

        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
        KnowledgePackage kp = new KnowledgeBase(rete).getKnowledgePackage();
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        // 100 noise + 1 alice
        for (int i = 0; i < 100; i++) session.insert(new Foo("noise-" + i));
        session.insert(new Foo("alice"));
        RuleExecutionResponse resp = session.fireRules();
        assertNotNull(resp);
        assertEquals(1, resp.getFiredRules().size(),
            "V5.83 调查: 1-pattern 100 noise + 1 alice(noise-first 顺序),期望 firedRules=1");
    }

    @Test
    @DisplayName("V5.83 — Given 1 rule + 1 Foo(\"alice\") + 100 Foo(noise) insert 在后,When fireRules,Then firedRules=1")
    void singleRuleMatchFirstThenNoise() {
        Rule r = new Rule();
        r.setName("R1"); r.setSalience(0);
        And and = new And();
        Criteria c = new Criteria();
        Left left = new Left(); left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory("Foo"); part.setVariableName("name"); part.setVariableLabel("name");
        part.setDatatype(Datatype.String);
        left.setLeftPart(part); c.setLeft(left); c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue(); sv.setContent("alice"); c.setValue(sv);
        and.addCriterion(c);
        Lhs lhs = new Lhs(); lhs.setCriterion(and); r.setLhs(lhs);

        VariableLibrary lib = new VariableLibrary();
        VariableCategory cat = new VariableCategory();
        cat.setName("Foo"); cat.setType(CategoryType.Clazz); cat.setClazz(Foo.class.getName());
        Variable v = new Variable(); v.setName("name"); v.setLabel("name"); v.setType(Datatype.String); v.setAct(Act.In);
        cat.setVariables(Collections.singletonList(v));
        lib.addVariableCategory(cat);
        ResourceLibrary rl = new ResourceLibrary(Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());

        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
        KnowledgePackage kp = new KnowledgeBase(rete).getKnowledgePackage();
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        session.insert(new Foo("alice"));
        for (int i = 0; i < 100; i++) session.insert(new Foo("noise-" + i));
        RuleExecutionResponse resp = session.fireRules();
        assertNotNull(resp);
        assertEquals(1, resp.getFiredRules().size(),
            "V5.83 调查: 1-pattern 1 alice + 100 noise(alice-first 顺序),期望 firedRules=1");
    }
}
