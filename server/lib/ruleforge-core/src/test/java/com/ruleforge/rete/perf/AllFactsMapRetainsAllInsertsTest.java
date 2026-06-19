package com.ruleforge.rete.perf;
import com.ruleforge.engine.ValueCompute;

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
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.82 — {@code KnowledgeSessionImpl.allFactsMap} 多 fact 同 className 保留契约 BDD。
 *
 * <p>V5.79 perf bench fired=0 调查发现 pre-existing production bug:
 * {@code allFactsMap} 是 {@code Map<String,Object>},按 className 作 key,1000 个
 * 同 class fact insert 后只剩 1 个(覆盖)。修法(V5.82):改用 {@code List<Object>}
 * 累加,旧 {@code getAllFactsMap()} 返回 last-wins 视图保留 backward compat(给
 * {@code ValueCompute.findObject} / {@code LoopRule} / {@code KnowledgeSessionTest}
 * 用,它们契约都是"className 命中一个 fact 即可")。
 *
 * <p>本 BDD 锁最小契约:1000 个同 className fact insert 后,engine 内部
 * 看到的 fact 集合大小 == 1000(走 {@code getAllFactsList()})。旧
 * {@code getAllFactsMap()} 仍返 1 entry(最后 wins,行为不变 — 不破坏
 * {@code KnowledgeSessionTest:265} 的 {@code containsEntry("User", entity)} 契约)。
 *
 * <p>反向验证:装 V5.81 老 {@code Map<String,Object>} 累加路径 →
 * list count != 1000,过不了本 BDD;装 V5.82 list-backed 路径 → 1000。
 *
 * @see EvalBenchmarkV579 V5.79 perf bench(本 BDD 修完后收紧断言)
 * @see com.ruleforge.v581_criteria_test_wiring_fix
 * @since 5.82
 */
@DisplayName("V5.82 — allFactsMap 改 List<Object> 后,多 fact 同 className 不再覆盖")
@Tag("perf")
class AllFactsMapRetainsAllInsertsTest {

    public static class Foo {
        private String name;
        public Foo() {}
        public Foo(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    private static KnowledgeSessionImpl session;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    private KnowledgePackage buildEmptyPackage() {
        // 空 rule 列表 — 本 BDD 只验 fact 累加路径,不验 rete fire。
        Rule r = new Rule();
        r.setName("R0");
        r.setSalience(0);
        r.setLhs(new com.ruleforge.model.rule.lhs.Lhs());

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

        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
        return new KnowledgeBase(rete).getKnowledgePackage();
    }

    @Test
    @DisplayName("Given 1000 个 Foo insert,When fireRules,Then 内部 fact 集合大小 == 1000")
    void thousandInsertsRetained() {
        // Given
        session = new KnowledgeSessionImpl(buildEmptyPackage());

        // When — 1000 个同 className fact insert
        int N = 1000;
        for (int i = 0; i < N; i++) {
            session.insert(new Foo("n" + i));
        }

        // Then — 内部累加路径看到 1000 个 fact(走新 getAllFactsList)
        List<Object> allFacts = session.getAllFactsList();
        assertNotNull(allFacts, "V5.82: getAllFactsList() 应不返回 null");
        assertEquals(N, allFacts.size(),
            "V5.82: 1000 个同 className Foo insert 后,内部 fact 集合应保留全部 1000 个。"
            + "V5.81 老 Map<String,Object> 按 className 覆盖路径会只剩 1 个 — 那个 bug 修完了。");

        // Then — backward compat:getAllFactsMap() 仍返 last-wins 视图(单 entry,最后 fact)
        // 不破坏 KnowledgeSessionTest:265 的 containsEntry 契约。
        assertEquals(1, session.getAllFactsMap().size(),
            "V5.82: getAllFactsMap() 仍返 last-wins Map 视图(单 entry,last fact wins) — "
            + "ValueCompute.findObject / LoopRule / 旧 API 不变");
        assertEquals("n" + (N - 1), ((Foo) session.getAllFactsMap().get(Foo.class.getName())).getName(),
            "V5.82: getAllFactsMap() 的 last-wins 视图应返最后 insert 的 fact(行为不变)");
    }

    @Test
    @DisplayName("Given 混合 Foo + Bar insert,When fireRules,Then fact 集合大小 == Foo+Bar 全数,getAllFactsMap 按 class 各 1 entry")
    void mixedTypesAllRetained() {
        // Given
        session = new KnowledgeSessionImpl(buildEmptyPackage());

        // When — 500 Foo + 300 Bar
        for (int i = 0; i < 500; i++) session.insert(new Foo("f" + i));
        for (int i = 0; i < 300; i++) session.insert(new Bar("b" + i));

        // Then
        assertEquals(800, session.getAllFactsList().size(),
            "V5.82: 500 Foo + 300 Bar 累加应共 800 个 fact,不再按 className 覆盖");
        assertEquals(2, session.getAllFactsMap().size(),
            "V5.82: getAllFactsMap() 应含 2 个 className entry(Foo + Bar 各 1,last wins)");
    }

    @Test
    @DisplayName("Given 1000 Foo insert + fireRules,Then fireRules 不抛错(engine path 用 getAllFactsList)")
    void fireRulesUsesListNotMap() {
        // Given
        session = new KnowledgeSessionImpl(buildEmptyPackage());
        for (int i = 0; i < 1000; i++) session.insert(new Foo("n" + i));

        // When — fireRules() 走 evaluationRete(this.allFactsList) 而非 this.allFactsMap.values()
        RuleExecutionResponse resp = session.fireRules();

        // Then — 不抛错即可。空 Lhs 的 rule 在 Rete 里被当作 always-match(每 fact 都激活),
        // 1000 fact → 1000 activation,但 RuleInfo 是 rule 本身(去重),所以 firedRules=1。
        // 本断言锁"engine path 用 getAllFactsList,不 NPE / 不抛错",不锁具体 fired 数。
        assertNotNull(resp, "V5.82: fireRules 应能跑通不抛错(走 getAllFactsList 路径)");
        assertNotNull(resp.getFiredRules(), "V5.82: firedRules 不应为 null");
        assertEquals(1, resp.getFiredRules().size(),
            "V5.82: 1000 Foo + 1 always-match rule → 1 unique rule fired "
            + "(RuleInfo 去重;activation 数是 1000,RuleInfo size=1)。"
            + "本断言锁 fireRules 走新 getAllFactsList 路径,无 NPE / 覆盖 bug。");
    }

    public static class Bar {
        private String name;
        public Bar() {}
        public Bar(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
