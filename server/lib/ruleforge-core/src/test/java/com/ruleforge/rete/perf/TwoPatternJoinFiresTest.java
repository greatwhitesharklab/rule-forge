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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.82 — 2-pattern join 最小 BDD 调查:为什么 EvalBenchmarkV579 修完 allFactsMap
 * 后仍 firedRules=0?
 *
 * <p>本 BDD 锁最小场景:1 rule(Person(name=="alice") + Address(street=="Main")) +
 * 1 Person("alice") + 1 Address("Main"),期望 firedRules=1。
 * 用来定位是 2-pattern 路径有别的问题,还是 1-pattern 就够。
 *
 * @since 5.82
 */
@DisplayName("V5.82 — 2-pattern join 最小 BDD")
@Tag("perf")
class TwoPatternJoinFiresTest {

    public static class Person { private String name; public Person(){}public Person(String n){name=n;} public String getName(){return name;} public void setName(String n){name=n;} }
    public static class Address { private String street; public Address(){}public Address(String s){street=s;} public String getStreet(){return street;} public void setStreet(String s){street=s;} }

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    private ResourceLibrary buildLibrary() {
        VariableLibrary pl = new VariableLibrary();
        VariableCategory pc = new VariableCategory();
        pc.setName("Person"); pc.setType(CategoryType.Clazz); pc.setClazz(Person.class.getName());
        Variable pv = new Variable(); pv.setName("name"); pv.setLabel("name"); pv.setType(Datatype.String); pv.setAct(Act.In);
        pc.setVariables(Collections.singletonList(pv));
        pl.addVariableCategory(pc);
        VariableLibrary al = new VariableLibrary();
        VariableCategory ac = new VariableCategory();
        ac.setName("Address"); ac.setType(CategoryType.Clazz); ac.setClazz(Address.class.getName());
        Variable av = new Variable(); av.setName("street"); av.setLabel("street"); av.setType(Datatype.String); av.setAct(Act.In);
        ac.setVariables(Collections.singletonList(av));
        al.addVariableCategory(ac);
        return new ResourceLibrary(Arrays.asList(pl, al), new ArrayList<>(), new ArrayList<>());
    }

    private Rule buildJoinRule() {
        Rule r = new Rule();
        r.setName("R1");
        r.setSalience(0);
        And and = new And();
        // Person(name == "alice")
        Criteria c1 = new Criteria();
        Left l1 = new Left(); l1.setType(LeftType.variable);
        VariableLeftPart v1 = new VariableLeftPart(); v1.setVariableCategory("Person"); v1.setVariableName("name"); v1.setVariableLabel("name"); v1.setDatatype(Datatype.String);
        l1.setLeftPart(v1); c1.setLeft(l1); c1.setOp(Op.Equals);
        SimpleValue sv1 = new SimpleValue(); sv1.setContent("alice"); c1.setValue(sv1);
        and.addCriterion(c1);
        // Address(street == "Main")
        Criteria c2 = new Criteria();
        Left l2 = new Left(); l2.setType(LeftType.variable);
        VariableLeftPart v2 = new VariableLeftPart(); v2.setVariableCategory("Address"); v2.setVariableName("street"); v2.setVariableLabel("street"); v2.setDatatype(Datatype.String);
        l2.setLeftPart(v2); c2.setLeft(l2); c2.setOp(Op.Equals);
        SimpleValue sv2 = new SimpleValue(); sv2.setContent("Main"); c2.setValue(sv2);
        and.addCriterion(c2);
        Lhs lhs = new Lhs(); lhs.setCriterion(and); r.setLhs(lhs);
        return r;
    }

    @Test
    @DisplayName("Given 1 rule(Person+Address join) + 1 Person(\"alice\") + 1 Address(\"Main\"),When fireRules,Then firedRules=1")
    void twoPatternOneFactEachFires() {
        KnowledgePackage kp = new KnowledgeBase(new ReteBuilder().buildRete(
            Collections.singletonList(buildJoinRule()), buildLibrary())).getKnowledgePackage();
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        session.insert(new Person("alice"));
        session.insert(new Address("Main"));
        RuleExecutionResponse resp = session.fireRules();
        assertNotNull(resp);
        assertNotNull(resp.getFiredRules());
        // 反验:V5.82 allFactsList 修后 2-pattern 1+1 fact 最小场景应能 fire
        // (SingleRuleFiresBDD 1-pattern 已 fire=1,本 BDD 验 2-pattern)
        assertEquals(1, resp.getFiredRules().size(),
            "V5.82: 2-pattern 最小场景期望 firedRules=1。"
            + "EvalBenchmarkV579 fired=0 可能是 2-pattern join 路径有别的问题,"
            + "或大 workload 才暴露。");
    }

    @Test
    @DisplayName("Given 1 rule + 3 noise + 1 matching pair,When fireRules,Then firedRules=1")
    void twoPatternWithNoise() {
        KnowledgePackage kp = new KnowledgeBase(new ReteBuilder().buildRete(
            Collections.singletonList(buildJoinRule()), buildLibrary())).getKnowledgePackage();
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        // noise first
        session.insert(new Person("bob"));
        session.insert(new Address("Side St"));
        // match second
        session.insert(new Person("alice"));
        session.insert(new Address("Main"));
        RuleExecutionResponse resp = session.fireRules();
        System.out.println("[V5.83 DEBUG] noise-first fired: " + (resp.getFiredRules()==null?0:resp.getFiredRules().size()));
        assertNotNull(resp.getFiredRules());
        assertEquals(1, resp.getFiredRules().size(),
            "V5.83: noise-first 顺序下也期望 firedRules=1(锁 beta-memory fix)");
    }
}
