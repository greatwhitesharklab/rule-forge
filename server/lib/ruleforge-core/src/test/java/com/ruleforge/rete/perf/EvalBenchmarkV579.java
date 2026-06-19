package com.ruleforge.rete.perf;
import com.ruleforge.engine.Path;

import com.ruleforge.rete.test.EngineContextWirer;

import com.ruleforge.action.BsfVariableProvider;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.resource.ResourceBuilder;
import com.ruleforge.builder.resource.ResourceProvider;
import com.ruleforge.debug.DebugWriter;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Act;
import com.ruleforge.model.library.variable.CategoryType;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.library.variable.VariableLibrary;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.builder.AndBuilder;
import com.ruleforge.model.rete.builder.CriteriaBuilder;
import com.ruleforge.model.rete.builder.ReteBuilder;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.parse.ActionParser;
import com.ruleforge.parse.CriterionParser;
import com.ruleforge.plugin.EnginePluginRegistry;
import com.ruleforge.engine.EngineContext;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.runtime.assertor.Assertor;
import com.ruleforge.engine.AssertorEvaluator;
import com.ruleforge.runtime.assertor.EqualsAssertor;
import com.ruleforge.engine.ValueCompute;
import com.ruleforge.engine.RuleExecutionResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.79 — Java RETE EvalBenchmark baseline (DRL-only perf regression suite)。
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
 * <p><b>V5.79 适配变更</b>(V5.46 → V5.78+ API 迁移 + V5.78 回归隔离):
 * <ul>
 *   <li>V5.76 删 {@code Utils.applicationContext} 静态字段,改用
 *       {@link EngineContext} 静态桥;本测试走 {@link EngineContext#init} 注入
 *       mock registry(深调用点 DI 唯一 sanctioned 通道,见 CLAUDE.md "核心不渗
 *       Spring")</li>
 *   <li>V5.78.1 起 {@link com.ruleforge.model.rete.builder.ReteBuilder} 用
 *       {@link EnginePluginRegistry#getCriterionBuilders()} 收集 criterion builders;
 *       V5.46 只注入 {@link CriteriaBuilder},V5.79 还要注入 {@link AndBuilder}(多 pattern
 *       隐式 AND 需要)。{@code setPluginRegistry} 接收 registry,本测试绕过 Spring
 *       直接给静态 field 灌 {@code Arrays.asList(new CriteriaBuilder(), new AndBuilder())}</li>
 *   <li>{@code AssertorEvaluator} / {@code ValueCompute} 走 Mockito mock,只为提供
 *       真实 {@link EqualsAssertor}(rule fire 要走 equals 比较)挂到 registry 的
 *       {@code getAssertors()} collection</li>
 *   <li><b>V5.78 回归隔离</b>:V5.78 PR #142 漏了 DRL → Rule model → ReteBuilder 路径
 *       的 {@code VariableLeftPart.variableCategory} 字段未填(DrlDeserializer.toCriteria
 *       构造时不调 setVariableCategory),{@code BuildContextImpl.getObjectType} 拿到 null
 *       抛 {@code "Variable category [null] not exist"}。此 bug 留 V5.80 修(已建
 *       TD-17.0c),V5.79 perf bench 改用手工构造 {@link Rule} + {@link VariableLeftPart}
 *       (variableCategory 用 "Person" / "Address"),workload 语义保持 — 测的是
 *       ReteBuilder + KnowledgeSessionImpl 真实 hot path,DRL 解析不在 perf bench 范围。
 *       之前 V5.46 走 DrlDeserializer → RulesRebuilder 路径是 Spring 收集的;
 *       V5.78.1 后 DrlDeserializer 不经 RulesRebuilder 暴露了这个 gap。</li>
 * </ul>
 *
 * <p>所有 RETE 路径 100% production 代码 — {@code ReteBuilder.buildRete(rules, lib)} +
 * {@code KnowledgeSessionImpl.fireRules()}。
 *
 * @see EvalBenchmark V5.46 原版(本类是其 V5.78+ 适配,workload 不变)
 * @since 5.79
 */
@Tag("perf")
@DisplayName("V5.79 — Java RETE EvalBenchmark baseline (DRL-only)")
class EvalBenchmarkV579 {

    // ====== workload 数据 ======

    private static final int N = 1000;
    private final Person[] persons = new Person[N];
    private final Address[] addresses = new Address[N];

    // ====== setup:V5.78+ API 装配(无 Spring) ======

    @BeforeAll
    static void wireEngineContext() throws Exception {
        // V5.81:走共享 EngineContextWirer(真实 ValueCompute,不再 Mockito mock)。
        // 旧 V5.79/V5.46 套路 Mockito mock ValueCompute 没 stub complexValueCompute,
        // 默认返 null → criteria.evaluate right side 永远是 null → equals(null) 永不命中
        // → 所有 rule 都不 fire。V5.81 TD-19.2 修(见 SingleRuleFiresBDD 调查 trace + [[v580-drl-regression-fix]])。
        EngineContextWirer.wire();
    }

    private void prepareData() {
        for (int i = 0; i < N; i++) {
            // V5.91 — AtomicLong 计数器替代 UUID.randomUUID().toString()
            addresses[i] = new Address(FactIds.next("a"));
            persons[i] = new Person(FactIds.next("p")).setAddress(addresses[i]);
        }
        addresses[250] = new Address("Main Street");
        persons[250] = new Person("Mario").setAddress(addresses[250]);
        addresses[500] = new Address("First Street");
        persons[500] = new Person("Duncan").setAddress(addresses[500]);
        addresses[750] = new Address("Second Street");
        persons[750] = new Person("Toshiya").setAddress(addresses[750]);
    }

    // ====== 手工构造 Rule(绕开 V5.78 DrlDeserializer bug) ======
    // 见类注释"V5.78 回归隔离"。workload 跟 V5.46 镜像 mariofusco 一致,DRL 解析
    // 路径不在 perf bench 范围,这里手填 VariableLeftPart.variableCategory 让
    // ReteBuilder.buildRete 走通。

    private static final String[] RULE_NAMES = { "R1", "R2", "R3" };
    private static final String[] PERSON_NAMES = { "Mario", "Duncan", "Toshiya" };
    private static final String[] STREETS = { "Main Street", "First Street", "Second Street" };

    private Rule buildRuleWithFieldFilters(String ruleName, String personName, String street) {
        Rule r = new Rule();
        r.setName(ruleName);
        r.setSalience(0);

        // R1: Person(name == "Mario"), Address(street == "Main Street")
        And and = new And();
        and.addCriterion(buildEqualsCriteria("Person", "name", personName, Datatype.String));
        and.addCriterion(buildEqualsCriteria("Address", "street", street, Datatype.String));

        com.ruleforge.model.rule.lhs.Lhs lhs = new com.ruleforge.model.rule.lhs.Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);
        return r;
    }

    private Rule buildRuleWithEval(String ruleName) {
        // eval 模式:用跟 no_eval 同样的 Person+Address 字段过滤,但 value 设
        // 成 "__no_match_xxx" — 99...9 个 fact 全是 random UUID,3 个 special fact
        // value 也不匹配。raw RETE path 跟 no_eval 一样走 alpha+beta join,只差
        // value 不同。V5.46 原版用 `eval(true)` 是 DRL 特殊形式,V5.79 手构 Rule
        // 不支持(eval 是 DRL grammar V5.42.1 引入的 CommonFunctionLeftPart 路径),
        // 改用字段过滤 value 不可达 模拟 raw RETE 路径,期望 0 fired。
        Rule r = new Rule();
        r.setName(ruleName);
        r.setSalience(0);

        And and = new And();
        and.addCriterion(buildEqualsCriteria("Person", "name", "__no_match_person__", Datatype.String));
        and.addCriterion(buildEqualsCriteria("Address", "street", "__no_match_address__", Datatype.String));

        com.ruleforge.model.rule.lhs.Lhs lhs = new com.ruleforge.model.rule.lhs.Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);
        return r;
    }

    private Criteria buildEqualsCriteria(String variableCategory, String property, String value, Datatype datatype) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(variableCategory);
        part.setVariableName(property);
        part.setVariableLabel(property);
        part.setDatatype(datatype);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue();
        sv.setContent(value);
        c.setValue(sv);
        return c;
    }

    private Criteria buildNoFilterCriteria(String variableCategory) {
        // 无字段过滤的 pattern — VariableLeftPart.variableName = variableCategory
        // (跟 DrlDeserializer 路径对齐,见 DrlDeserializer line 327-330)。
        // value 用 "__no_match__" + Op.Equals → 全 false,期望 0 fired。
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(variableCategory);
        part.setVariableName(variableCategory);
        part.setVariableLabel(variableCategory);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue();
        sv.setContent("__no_match__");
        c.setValue(sv);
        return c;
    }

    // ====== 构造 Rete(KnowledgePackage 不可变,复用) + 每次新 session ======

    private ResourceLibrary buildResourceLibrary() {
        // V5.78+ 路径下,BuildContextImpl.getObjectType 按 VariableLeftPart.variableCategory
        // 查 ResourceLibrary.getVariableCategory(name),name 找不到抛
        // "Variable category [Person] not exist"。手工建 Person/Address 两个 category
        // (clazz=FQCN,跟 ObjectTypeNode.support(object) 用的 object.getClass().getName() 对齐)。
        // 每个 category 含对应字段(供 ReteBuilder 后续 validate 字段引用)。
        VariableLibrary personLib = new VariableLibrary();
        personLib.addVariableCategory(buildCategory("Person", Person.class.getName(),
            new String[]{"name", "address"}));
        VariableLibrary addressLib = new VariableLibrary();
        addressLib.addVariableCategory(buildCategory("Address", Address.class.getName(),
            new String[]{"street"}));
        List<VariableLibrary> libs = new ArrayList<>();
        libs.add(personLib);
        libs.add(addressLib);
        return new ResourceLibrary(libs, new ArrayList<>(), new ArrayList<>());
    }

    private VariableCategory buildCategory(String name, String clazz, String[] fieldNames) {
        VariableCategory cat = new VariableCategory();
        cat.setName(name);
        cat.setType(CategoryType.Clazz);
        cat.setClazz(clazz);
        List<Variable> vars = new ArrayList<>();
        for (String fn : fieldNames) {
            Variable v = new Variable();
            v.setName(fn);
            v.setLabel(fn);
            v.setType(Datatype.String);
            v.setAct(Act.In);
            vars.add(v);
        }
        cat.setVariables(vars);
        return cat;
    }

    private KnowledgePackage buildKnowledgePackageNoEval() {
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rules.add(buildRuleWithFieldFilters(RULE_NAMES[i], PERSON_NAMES[i], STREETS[i]));
        }
        assertEquals(3, rules.size());
        Rete rete = new ReteBuilder().buildRete(rules, buildResourceLibrary());
        return new KnowledgeBase(rete).getKnowledgePackage();
    }

    private KnowledgePackage buildKnowledgePackageEval() {
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rules.add(buildRuleWithEval(RULE_NAMES[i]));
        }
        assertEquals(3, rules.size());
        Rete rete = new ReteBuilder().buildRete(rules, buildResourceLibrary());
        return new KnowledgeBase(rete).getKnowledgePackage();
    }

    /**
     * V5.79.2 — 3 pattern 链式 join:3 个 Person fact(Person_a, Person_b, Person_c)
     * 加 1 个 Address 共同匹配。无 binding,所以是 3-way cross join alpha + 2 beta
     * join — 模拟 V5.77 accumulate 的多源 pattern 场景。
     *
     * <p>为简单化本 bench 复用 Person/Address 现有 VariableCategory(只有 2 个
     * category)— 3 个 pattern 都用 Person,后 2 个 pattern value 不可达,
     * firedRules=0。raw perf 数字反映 V5.78+ 多源 join 路径成本。
     */
    private KnowledgePackage buildKnowledgePackage3WayJoin() {
        Rule r = new Rule();
        r.setName("R3Way");
        r.setSalience(0);
        And and = new And();
        and.addCriterion(buildEqualsCriteria("Person", "name", "Mario", Datatype.String));
        and.addCriterion(buildEqualsCriteria("Person", "name", "Duncan", Datatype.String));
        and.addCriterion(buildEqualsCriteria("Address", "street", "Main Street", Datatype.String));
        com.ruleforge.model.rule.lhs.Lhs lhs = new com.ruleforge.model.rule.lhs.Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);

        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), buildResourceLibrary());
        return new KnowledgeBase(rete).getKnowledgePackage();
    }

    /**
     * V5.79.2 — 5 条 rule 并列,每条 Person(name == X) + Address(street == Y)
     * 不同 field filter,模拟 V5.77 Java class import 路径的 reflection 注册
     * 放大效应(V5.77 #141)— 单个 .drl 多个 fact type 引用走 addJavaImport 路径,
     * 编译时 per-fact-type 反射 addField。perf 数字反映此路径成本。
     */
    private KnowledgePackage buildKnowledgePackage5Rules() {
        String[][] pairs = {
            {"Mario",   "Main Street"},
            {"Duncan",  "First Street"},
            {"Toshiya", "Second Street"},
            {"Mario",   "First Street"},  // 不匹配,测 raw filter 路径
            {"Duncan",  "Second Street"}, // 不匹配
        };
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rules.add(buildRuleWithFieldFilters("R" + (i + 1), pairs[i][0], pairs[i][1]));
        }
        Rete rete = new ReteBuilder().buildRete(rules, buildResourceLibrary());
        return new KnowledgeBase(rete).getKnowledgePackage();
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

    private Stats measure(KnowledgePackage kp, int warmup, int iters) {
        int prevFired = -1;
        for (int i = 0; i < warmup; i++) {
            int fired = run(kp);
            if (prevFired < 0) prevFired = fired;
        }
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
            "[V5.79 EvalBenchmark] %-12s | n=%d | fired=%d (warmup=%d) | min=%.2fms p50=%.2fms mean=%.2fms max=%.2fms%n",
            label, s.nanos.length, s.firedRules, s.warmupFiredRules,
            s.minMs(), s.p50Ms(), s.meanMs(), s.maxMs());
    }

    // ====== BDD:实际跑 ======

    @Test
    @DisplayName("no_eval 字段过滤 — 测 alpha index 收益")
    void benchNoEval() {
        prepareData();
        KnowledgePackage kp = buildKnowledgePackageNoEval();
        Stats s = measure(kp, 5, 50);
        report("no_eval", s);
        // V5.83 note:TD-19.5.4 修了 rete sticky state 缺陷(per-fact clean + resetStickyStateOnly,
        // 保留 Path.passed 累积)— 见 [[v582-allfactsmap-rewrite]] TD-19.5.4。3 条 special pair
        // rule(Mario+Main / Duncan+First / Toshiya+Second)在 1000 P/A noise + 3 special pair
        // 场景下 3 条规则都应 fire(每条对应 1 个 special pair),期望 firedRules=3。
        assertEquals(3, s.firedRules, "V5.83: 3 special pair rule 在 1000 noise + 3 special 场景下应 fire 3 次(每 rule 1 次),见 docs/notes/v583-rete-sticky-state-fix.md");
    }

    @Test
    @DisplayName("eval 全 no-op 字段过滤 — 测 raw RETE baseline")
    void benchEval() {
        prepareData();
        KnowledgePackage kp = buildKnowledgePackageEval();
        Stats s = measure(kp, 5, 50);
        report("eval", s);
        // eval case:无 binding 跨 pattern join,V5.46 同样 0 fired(见 V5.46 README:
        // "Rust 端 0 fired,只测 raw 时间")。V5.79 一致 — raw RETE 路径 perf 0.3-0.5ms。
        assertEquals(0, s.firedRules, "eval 应 0 fired(无 binding 或 value 不匹配)");
    }

    /**
     * V5.79.2 — V5.77 grammar 扩展覆盖:3 pattern 链式 join(模拟 accumulate reverse
     * 的多源 pattern 场景,见 V5.77 #141)。比 no_eval 多一个 alpha 节点 + 一次
     * beta join,perf 数字反映 V5.78+ engine 在多源 pattern 下的回归。
     */
    @Test
    @DisplayName("no_eval_3way_join — 3 pattern 链式 join(测 V5.77 accumulate 多源场景)")
    void benchNoEval3WayJoin() {
        prepareData();
        KnowledgePackage kp = buildKnowledgePackage3WayJoin();
        Stats s = measure(kp, 5, 50);
        report("no_eval_3way", s);
        // V5.83 note:3-pattern 链式 join,要求 Mario+Duncan+Main Street 三者共存。
        // 1000 P/A noise 中无 Mario+Duncan 同事 + Main Street 的三元组(只有 special pair 不构成三元组),
        // 期望 firedRules=0。perf 数字反映 V5.78+ 多源 join 路径成本。
        assertEquals(0, s.firedRules, "V5.83: 3-pattern 链式 join 期望 firedRules=0(无 Mario+Duncan+Main Street 三元组)");
    }

    /**
     * V5.79.2 — V5.77 grammar 扩展覆盖:5 个并列独立 alpha 过滤(测大规模单 pattern
     * 字段过滤场景,V5.77 Java class import 的 reflection 注册路径对 alpha 节点
     * 数量有放大效应)。5 条 rule,每条不同字段过滤。
     */
    @Test
    @DisplayName("no_eval_5_rules — 5 条 rule 并列(测 V5.77 Java class import 路径)")
    void benchNoEval5Rules() {
        prepareData();
        KnowledgePackage kp = buildKnowledgePackage5Rules();
        Stats s = measure(kp, 5, 50);
        report("no_eval_5r", s);
        // V5.83 note:5 条 rule,每条 Person(name==X) + Address(street==Y)。
        // 3 special pair rule(Mario+Main, Duncan+First, Toshiya+Second)+ 2 跨 special rule
        // (Mario+First, Duncan+Second)— 跨 special 也匹配(Mario+First 都存在)→ 5 条都 fire。
        assertEquals(5, s.firedRules, "V5.83: 5 条 rule 在 1000 noise + 3 special 场景下都匹配(每 rule 1 次),期望 firedRules=5");
    }

    // ====== POJO ======

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
