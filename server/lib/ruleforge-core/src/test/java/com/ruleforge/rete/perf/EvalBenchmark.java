package com.ruleforge.rete.perf;

import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.ir.drl.DatatypeResolver;
import com.ruleforge.ir.drl.DrlResource;
import com.ruleforge.ir.drl.DrlResourceBuilder;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.builder.CriteriaBuilder;
import com.ruleforge.model.rete.builder.ReteBuilder;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import org.springframework.context.ApplicationContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.46 — Java RETE EvalBenchmark baseline。
 *
 * <p>镜像 {@code mariofusco/drools-benchmark EvalBenchmark.run()}(2020)workload:
 * <ul>
 *   <li>1000 个 Person + 1000 个 Address(其中 250/500/750 三个 special)</li>
 *   <li>3 条 rule(name + street 双向精确匹配),期望 fire 3 次</li>
 *   <li>单次完整 insert(2000 fact) + fireAllRules 耗时</li>
 * </ul>
 *
 * <p>本测试**不**用 JMH(避免引入新 dep)— 用 {@code System.nanoTime()} +
 * 5 轮 warmup + 50 轮 measurement,返 min/p50/max/ms-per-fire。
 *
 * <p>两个 case:
 * <ol>
 *   <li>{@code no_eval} — OOPATH 字段过滤(测 alpha index 收益)</li>
 *   <li>{@code eval} — 全部塞 {@code eval()} 调用(测 raw RETE 性能 baseline)</li>
 * </ol>
 *
 * <p>所有路径 100% production 代码 — {@code ReteBuilder.buildRete(rules, lib)} +
 * {@code KnowledgeSessionImpl.fireRules()}。
 */
@DisplayName("V5.46 — Java RETE EvalBenchmark baseline")
class EvalBenchmark {

    // ====== workload 数据 ======

    private static final int N = 1000;
    private final Person[] persons = new Person[N];
    private final Address[] addresses = new Address[N];

    // ====== setup:每条 fact type 注册到 DatatypeResolver,让 DRL 解析能找到 ======

    @BeforeAll
    static void wireCriterionBuilders() throws Exception {
        // ReteBuilder.criterionBuilders 是 private static,生产靠 Spring 注入。
        // 单元测试没 Spring,反射注入。V5.45.x 一直这么搞。
        // 本 benchmark 用纯字段过滤(no junction),只需要 CriteriaBuilder。
        java.lang.reflect.Field f = ReteBuilder.class.getDeclaredField("criterionBuilders");
        f.setAccessible(true);
        f.set(null, Arrays.asList(new CriteriaBuilder()));

        // Utils.applicationContext 是 Spring 静态 context。KnowledgeSessionImpl
        // 初始化时调 Utils.getApplicationContext() 拿 KnowledgePackageService。
        // 用 Mockito mock 一个空 ApplicationContext,getBean 返 null,所有
        // session init 调用都走 try/catch fallback 路径。
        ApplicationContext mockCtx = org.mockito.Mockito.mock(ApplicationContext.class);
        org.mockito.Mockito.when(mockCtx.getBean(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(null);
        java.lang.reflect.Field uf = Utils.class.getDeclaredField("applicationContext");
        uf.setAccessible(true);
        uf.set(null, mockCtx);
    }

    private DatatypeResolver makeResolver() {
        DatatypeResolver r = new DatatypeResolver();
        r.register("Person", DatatypeResolver.TypeInfo.fact("Person",
            Arrays.asList("name", "address")));
        r.register("Address", DatatypeResolver.TypeInfo.fact("Address",
            Arrays.asList("street")));
        return r;
    }

    private void prepareData() {
        for (int i = 0; i < N; i++) {
            addresses[i] = new Address(UUID.randomUUID().toString());
            persons[i] = new Person(UUID.randomUUID().toString()).setAddress(addresses[i]);
        }
        addresses[250] = new Address("Main Street");
        persons[250] = new Person("Mario").setAddress(addresses[250]);
        addresses[500] = new Address("First Street");
        persons[500] = new Person("Duncan").setAddress(addresses[500]);
        addresses[750] = new Address("Second Street");
        persons[750] = new Person("Toshiya").setAddress(addresses[750]);
    }

    // ====== DRL 文本(3 条 rule,字段过滤 vs eval 两种写法) ======
    // V5.46 注:RuleForge DRL 4 grammar 不支持 Drools 风格的 `$p : Person(...)`
    // binding 提取 — visitor 不抽 binding(grep V5.42.4 DrlAstVisitor 验证)。
    // 同样不支持 cross-pattern reference `$a == $p.getAddress()`。
    // 所以本 bench 简化为"两个 pattern ANDed,各带独立字段过滤":
    //   - no_eval 走 alpha index 提前过滤
    //   - eval 走 raw eval() 调用
    // 仍能测 alpha index vs raw RETE 性能差异 — 这正是 EvalBenchmark 想测的。

    private static final String NO_EVAL_DRL =
        "rule \"R1\" when Person(name == \"Mario\"), Address(street == \"Main Street\") then end\n" +
        "rule \"R2\" when Person(name == \"Duncan\"), Address(street == \"First Street\") then end\n" +
        "rule \"R3\" when Person(name == \"Toshiya\"), Address(street == \"Second Street\") then end\n";

    private static final String EVAL_DRL =
        "rule \"R1\" when Person(), eval(true), Address(), eval(true) then end\n" +
        "rule \"R2\" when Person(), eval(true), Address(), eval(true) then end\n" +
        "rule \"R3\" when Person(), eval(true), Address(), eval(true) then end\n";

    // ====== 构造 Rete(KnowledgePackage 是不可变的,复用) + 每次新 session ======

    private KnowledgePackage buildKnowledgePackage(String drl) {
        DatatypeResolver resolver = makeResolver();
        List<Rule> rules = new DrlResourceBuilder(resolver)
            .build(new DrlResource(drl, "/bench/rules.drl"));
        assertNotNull(rules);
        assertEquals(3, rules.size(), "应解析出 3 条 rule");
        Rete rete = new ReteBuilder().buildRete(rules,
            new ResourceLibrary(new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        KnowledgeBase kb = new KnowledgeBase(rete);
        return kb.getKnowledgePackage();
    }

    // ====== 单次 run:insert 全部 + fireRules(用 fresh session 模拟 production per-request 模式) ======

    private int run(KnowledgePackage kp) {
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        for (int i = 0; i < N; i++) {
            session.insert(addresses[i]);
            session.insert(persons[i]);
        }
        RuleExecutionResponse resp = session.fireRules();
        return resp.getFiredRules() == null ? 0 : resp.getFiredRules().size();
    }

    // ====== warmup + measurement helper ======

    private Stats measure(String drl, int warmup, int iters) {
        prepareData();
        KnowledgePackage kp = buildKnowledgePackage(drl);
        // warmup
        int prevFired = -1;
        for (int i = 0; i < warmup; i++) {
            int fired = run(kp);
            if (prevFired < 0) prevFired = fired;
        }
        // measurement
        long[] times = new long[iters];
        int fired = -1;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            int f = run(kp);
            long t1 = System.nanoTime();
            times[i] = t1 - t0;
            fired = f;
        }
        return new Stats(times, fired, prevFired);
    }

    private static class Stats {
        final long[] nanos;
        final int firedRules;
        final int warmupFiredRules;

        Stats(long[] nanos, int fired, int warmup) {
            this.nanos = nanos;
            this.firedRules = fired;
            this.warmupFiredRules = warmup;
        }

        double minMs() { return min() / 1e6; }
        double p50Ms() { return percentile(50) / 1e6; }
        double maxMs() { return max() / 1e6; }
        double meanMs() { return mean() / 1e6; }

        private long min() { long m = Long.MAX_VALUE; for (long n : nanos) m = Math.min(m, n); return m; }
        private long max() { long m = 0; for (long n : nanos) m = Math.max(m, n); return m; }
        private long mean() { long s = 0; for (long n : nanos) s += n; return s / nanos.length; }
        private long percentile(int p) {
            long[] sorted = nanos.clone();
            Arrays.sort(sorted);
            int idx = (int) Math.round((p / 100.0) * (sorted.length - 1));
            return sorted[idx];
        }
    }

    private void report(String label, Stats s) {
        System.out.printf(
            "[V5.46 EvalBenchmark] %-12s | n=%d | fired=%d (warmup=%d) | min=%.2fms p50=%.2fms mean=%.2fms max=%.2fms%n",
            label, s.nanos.length, s.firedRules, s.warmupFiredRules,
            s.minMs(), s.p50Ms(), s.meanMs(), s.maxMs());
    }

    // ====== BDD:实际跑 ======

    @Test
    @DisplayName("no_eval 字段过滤 — 测 alpha index 收益")
    void benchNoEval() {
        Stats s = measure(NO_EVAL_DRL, 5, 50);
        report("no_eval", s);
        // 期望 3 rule fire(3 个 special Person/Address pair)
        assertEquals(3, s.firedRules, "no_eval 应 fire 3 条 rule");
    }

    @Test
    @DisplayName("eval 全在 eval() — 测 raw RETE baseline")
    void benchEval() {
        Stats s = measure(EVAL_DRL, 5, 50);
        report("eval", s);
        assertEquals(3, s.firedRules, "eval 应 fire 3 条 rule");
    }

    // ====== POJO(为避免改 dependency 用最简单的属性模型) ======

    public static class Person {
        private final String name;
        private Address address;
        public Person(String name) { this.name = name; }
        public String getName() { return name; }
        public Address getAddress() { return address; }
        public Person setAddress(Address a) { this.address = a; return this; }
    }

    public static class Address {
        private final String street;
        public Address(String street) { this.street = street; }
        public String getStreet() { return street; }
    }
}
