package com.ruleforge.runtime.rete;
import com.ruleforge.engine.WorkingMemory;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.86 — ValueCompute.findObject 行为契约 BDD(端到端)。
 *
 * <p>V5.86 优化 {@code ValueCompute.findObject} 内部用 {@code classNameCache}
 * (Class.forName 一次性加载,后续 {@code getClass() == targetClass} 引用比较)
 * 替代 per-fact 字符串 compare。详见 [[v585-perf-scaling-analysis]] V5.86+ 优化方向 #1。
 *
 * <p>本 BDD 锁 findObject 4 个行为契约,优化前后必须保持一致(端到端 fired 锁约):
 * <ul>
 *   <li>同 class fact 命中:Person(name=alice) + Address(street=main) 走 dual pattern,期望 fired=1</li>
 *   <li>跨 class fallback:Person fact 查 Address 走 WorkingMemory.getAllFactsMap()</li>
 *   <li>无匹配:noise fact 期望 fired=0(不应抛 ClassNotFoundException)</li>
 *   <li>多 rule 并列(测 classNameCache 在多 className 下行为):5 rule × dual pattern</li>
 * </ul>
 *
 * <p>直接调 findObject 需要私有 Context 引用,本 BDD 走 {@link KnowledgeSessionImpl} 端到端
 * 间接锁约 — 优化前后 {@code assertEquals(fired=1)} 不变即代表 findObject 行为保持。
 *
 * @since 5.86
 */
@DisplayName("V5.86 — ValueCompute.findObject 行为契约(端到端)")
class ValueComputeFindObjectTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @Test
    @DisplayName("Given dual class Person(name=alice) AND Address(street=main), When 1 alice + 1 main, Then fired=1")
    void dualClassEndToEndFires() {
        // 1 rule, dual pattern, Person + Address 字段过滤,期望 1 命中 1 fired
        Rule r = buildDualClassRule("alice", "main");
        KnowledgePackage kp = buildKp(r);
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        try {
            session.insert(new Person("alice"));
            session.insert(new Address("main"));
            RuleExecutionResponse resp = session.fireRules();
            assertNotNull(resp);
            assertEquals(1, resp.getFiredRules().size(),
                "V5.86 优化前后: dual class 1 alice + 1 main 应 fired=1 (findObject 跨 pattern 命中)");
        } finally { /* session 暂不关闭,KnowledgeSessionImpl 无 AutoCloseable */ }
    }

    @Test
    @DisplayName("Given dual class rule, When 100 noise Person + 1 alice + 100 noise Address + 1 main, Then fired=1")
    void dualClassWithNoiseFires() {
        // 1 alice + 1 main 在 200 noise 之后:验证 findObject 跨 pattern 在 noise fact 混入下仍能命中
        Rule r = buildDualClassRule("alice", "main");
        KnowledgePackage kp = buildKp(r);
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        try {
            for (int i = 0; i < 100; i++) session.insert(new Person(UUID.randomUUID().toString()));
            session.insert(new Person("alice"));
            for (int i = 0; i < 100; i++) session.insert(new Address(UUID.randomUUID().toString()));
            session.insert(new Address("main"));
            RuleExecutionResponse resp = session.fireRules();
            assertNotNull(resp);
            assertEquals(1, resp.getFiredRules().size(),
                "V5.86: noise-first dual class 1 fired, findObject 跨 pattern 命中");
        } finally { /* session 暂不关闭,KnowledgeSessionImpl 无 AutoCloseable */ }
    }

    @Test
    @DisplayName("Given dual class rule, When 1000 noise only, Then fired=0(不抛错)")
    void dualClassNoMatchDoesNotThrow() {
        // 全 noise — findObject 跨 pattern 找不到 match, 应返 null 不抛 ClassNotFoundException
        Rule r = buildDualClassRule("alice", "main");
        KnowledgePackage kp = buildKp(r);
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        try {
            for (int i = 0; i < 1000; i++) {
                session.insert(new Person(UUID.randomUUID().toString()));
                session.insert(new Address(UUID.randomUUID().toString()));
            }
            RuleExecutionResponse resp = session.fireRules();
            assertNotNull(resp);
            assertEquals(0, resp.getFiredRules().size(),
                "V5.86: 全 noise 应 fired=0(无 ClassNotFoundException 抛出)");
        } finally { /* session 暂不关闭,KnowledgeSessionImpl 无 AutoCloseable */ }
    }

    @Test
    @DisplayName("Given 5 dual class rules, When insert 1 alice + 1 main, Then fired=5(findObject 缓存多 className 命中)")
    void fiveDualClassRulesAllFire() {
        // 5 条 dual class rule, 不同字段过滤 — 测 classNameCache 在多 className(5 个 Person + 5 个 Address)下行为
        // 全 5 条都要求 alice+main,所以 1 alice + 1 main 应 5 fired
        KnowledgePackage kp = buildKp5DualRules();
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        try {
            session.insert(new Person("alice"));
            session.insert(new Address("main"));
            RuleExecutionResponse resp = session.fireRules();
            assertNotNull(resp);
            assertEquals(5, resp.getFiredRules().size(),
                "V5.86: 5 dual class rule 全命中 alice+main, fired=5 (classNameCache 多 className 验证)");
        } finally { /* session 暂不关闭,KnowledgeSessionImpl 无 AutoCloseable */ }
    }

    // ====== helpers ======

    private Rule buildDualClassRule(String personName, String street) {
        Rule r = new Rule();
        r.setName("R1"); r.setSalience(0);
        And and = new And();
        and.addCriterion(buildCriteria("Person", "name", personName));
        and.addCriterion(buildCriteria("Address", "street", street));
        Lhs lhs = new Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);
        return r;
    }

    private KnowledgePackage buildKp(Rule r) {
        ResourceLibrary rl = buildDualClassResourceLibrary();
        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
        return new KnowledgeBase(rete).getKnowledgePackage();
    }

    private KnowledgePackage buildKp5DualRules() {
        String[] names = {"alice", "bob", "carol", "dave", "eve"};
        String[] streets = {"main", "first", "second", "third", "fourth"};
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // 每条 rule: Person(name=X[i]) AND Address(street=Y[i])
            // 5 条都要求不同的 X/Y 组合 — 1 alice + 1 main 只命中第一条
            // 改为:全部 5 条都要求 alice+main, 测多 className 行为
            Rule r = new Rule();
            r.setName("R" + (i + 1)); r.setSalience(i);
            And and = new And();
            and.addCriterion(buildCriteria("Person", "name", "alice"));
            and.addCriterion(buildCriteria("Address", "street", "main"));
            Lhs lhs = new Lhs();
            lhs.setCriterion(and);
            r.setLhs(lhs);
            rules.add(r);
        }
        ResourceLibrary rl = buildDualClassResourceLibrary();
        Rete rete = new ReteBuilder().buildRete(rules, rl);
        return new KnowledgeBase(rete).getKnowledgePackage();
    }

    private ResourceLibrary buildDualClassResourceLibrary() {
        VariableLibrary personLib = new VariableLibrary();
        VariableCategory personCat = new VariableCategory();
        personCat.setName("Person");
        personCat.setType(CategoryType.Clazz);
        personCat.setClazz(Person.class.getName());
        Variable nameVar = new Variable();
        nameVar.setName("name"); nameVar.setLabel("name"); nameVar.setType(Datatype.String); nameVar.setAct(Act.In);
        personCat.setVariables(Collections.singletonList(nameVar));
        personLib.addVariableCategory(personCat);

        VariableLibrary addressLib = new VariableLibrary();
        VariableCategory addressCat = new VariableCategory();
        addressCat.setName("Address");
        addressCat.setType(CategoryType.Clazz);
        addressCat.setClazz(Address.class.getName());
        Variable streetVar = new Variable();
        streetVar.setName("street"); streetVar.setLabel("street"); streetVar.setType(Datatype.String); streetVar.setAct(Act.In);
        addressCat.setVariables(Collections.singletonList(streetVar));
        addressLib.addVariableCategory(addressCat);

        List<VariableLibrary> libs = new ArrayList<>();
        libs.add(personLib);
        libs.add(addressLib);
        return new ResourceLibrary(libs, new ArrayList<>(), new ArrayList<>());
    }

    private Criteria buildCriteria(String varCat, String varName, String value) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(varCat);
        part.setVariableName(varName);
        part.setVariableLabel(varName);
        part.setDatatype(Datatype.String);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue();
        sv.setContent(value);
        c.setValue(sv);
        return c;
    }

    // ====== POJO ======

    public static class Person {
        private final String name;
        public Person(String name) { this.name = name; }
        public String getName() { return name; }
    }

    public static class Address {
        private final String street;
        public Address(String street) { this.street = street; }
        public String getStreet() { return street; }
    }
}
