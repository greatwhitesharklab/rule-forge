package com.ruleforge.rete.perf;

import com.ruleforge.rete.test.EngineContextWirer;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.model.GeneralEntity;
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
import com.ruleforge.runtime.FactStore;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.engine.KnowledgeSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * V6.6 — {@code FactStore.getAllFactsMap()} cache micro-bench (no assert, perf only)。
 *
 * <p>测量 cache 命中 vs cache miss (写后) 的 wall-time 差异。
 * 对照参考:V5.79 perf bench "allFactsMap className-keyed 覆盖 pre-existing bug"
 * 是 V5.82 修法;V6.6 加 cache 是 follow-up,避免每 fact LHS 求值都新建 HashMap。
 *
 * <p>本 bench 不进默认 test suite (用 {@code @Tag("perf")} + JUnit 5 tagged exclusion)。
 * 跑法: {@code mvn test -pl lib/ruleforge-core -Dtest=FactStoreGetAllFactsMapCacheBench -Dgroups=perf}
 */
@Tag("perf")
class FactStoreGetAllFactsMapCacheBench {

    private static final int N = 10_000;
    private static final int WARMUP = 1000;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @Test
    void cacheHitWallTime() {
        FactStore store = new FactStore();
        for (int i = 0; i < N; i++) {
            store.add(new GeneralEntity("User"));
        }

        // warmup
        for (int i = 0; i < WARMUP; i++) {
            store.getAllFactsMap();
        }

        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            store.getAllFactsMap();
        }
        long elapsedNs = System.nanoTime() - start;
        System.out.printf("[V6.6] cache hit: %d calls in %d ms (%.3f us/call)%n",
            N, elapsedNs / 1_000_000, elapsedNs / 1000.0 / N);
    }

    @Test
    void cacheMissWallTime() {
        FactStore store = new FactStore();
        for (int i = 0; i < N; i++) {
            store.add(new GeneralEntity("User"));
        }

        // warmup
        for (int i = 0; i < WARMUP; i++) {
            store.add(new GeneralEntity("Address"));
            store.getAllFactsMap();
        }

        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            // cache miss: 每次 add 都失效
            store.add(new GeneralEntity("Address"));
            store.getAllFactsMap();
        }
        long elapsedNs = System.nanoTime() - start;
        System.out.printf("[V6.6] cache miss (write+read): %d cycles in %d ms (%.3f us/cycle)%n",
            N, elapsedNs / 1_000_000, elapsedNs / 1000.0 / N);
    }

    @Test
    void knowledgeSessionReused() {
        // 模拟 ValueCompute.findObject per-fact 调 getAllFactsMap 路径
        // KnowledgeSession 内插 N fact + fireRules, 测 wall-time
        Rule r = new Rule();
        r.setName("R0");
        r.setSalience(0);
        r.setLhs(new com.ruleforge.model.rule.lhs.Lhs());

        VariableLibrary lib = new VariableLibrary();
        VariableCategory cat = new VariableCategory();
        cat.setName("User");
        cat.setType(CategoryType.Clazz);
        cat.setClazz(GeneralEntity.class.getName());
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
        KnowledgePackage pkg = new KnowledgeBase(rete).getKnowledgePackage();

        // warmup
        for (int i = 0; i < 5; i++) {
            KnowledgeSession session = new KnowledgeSessionImpl(pkg);
            for (int j = 0; j < 100; j++) session.insert(new GeneralEntity("User"));
            session.getAllFactsMap();
        }

        long start = System.nanoTime();
        for (int i = 0; i < N; i++) {
            KnowledgeSession session = new KnowledgeSessionImpl(pkg);
            session.insert(new GeneralEntity("User"));
            // fireRules 内部会调 getAllFactsMap (ValueCompute.findObject 路径)
            session.fireRules();
        }
        long elapsedNs = System.nanoTime() - start;
        System.out.printf("[V6.6] session fireRules (cache hit per-session): %d cycles in %d ms (%.3f us/cycle)%n",
            N, elapsedNs / 1_000_000, elapsedNs / 1000.0 / N);
    }
}