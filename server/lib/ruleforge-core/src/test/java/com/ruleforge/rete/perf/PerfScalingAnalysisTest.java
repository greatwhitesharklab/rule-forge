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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.85 — perf scaling 分析。跑 N=500/1000/2000/5000/10000 fact,测 per-fact wall time
 * 随 N 的变化形态(linear vs super-linear),识别 rete hot path 是否有 O(n²) 风险。
 *
 * <p>不写 hot path 打点(不动 production code)— 改用 wall-time scaling 反推 perf 模型。
 * 配合 [[v584-incremental-reset-attempt]] 教训:基于 doc 推断优化方向不可信,先测数据。
 *
 * <p>两类 workload:
 * <ul>
 *   <li>single class — 1 rule / 1 pattern / N fact,1 ObjectTypeActivity 子树,简化基线</li>
 *   <li>dual class — 1 rule / 2 pattern (Person + Address) / N fact,2 ObjectTypeActivity 子树
 *       + 1 beta join,模拟真实 2-pattern join 场景</li>
 * </ul>
 *
 * <p>每个 N 跑 3 轮 warmup + 5 轮 measurement,取 median。输出格式:
 * <pre>
 * [V5.85 PerfScaling] single | N=1000 | fired=1 (warmup=1) | total=5.20ms per-fact=5.20us
 * </pre>
 *
 * @see EvalBenchmarkV579 V5.79 baseline 4 scenario
 * @since 5.85
 */
@Tag("perf")
@DisplayName("V5.85 — perf scaling 分析(per-fact cost vs N)")
class PerfScalingAnalysisTest {

    /** scaling 探针:覆盖 0.5K/1K/2K/5K/10K 区间,识别 linear / super-linear 拐点 */
    private static final int[] SCALING_N = {500, 1000, 2000, 5000, 10000};

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    /**
     * Single class — 1 rule, 1 pattern, N Person fact。
     * 1 ObjectTypeActivity 子树,无 beta join。perf 主要来自 CriteriaActivity.evaluate。
     */
    @Test
    @DisplayName("single class Person + 1 rule, N fact scaling")
    void singleClassScaling() {
        Rule r = buildRule("R1", "Person", "name", "alice");
        ResourceLibrary rl = buildResourceLibrary("Person", "name");
        KnowledgePackage kp = buildKp(r, rl);

        for (int n : SCALING_N) {
            runScalingN(kp, n, /*dualClass=*/false);
        }
    }

    /**
     * Dual class — 1 rule, 2 pattern (Person+Address),N Person fact + N Address fact。
     * 2 ObjectTypeActivity 子树 + 1 beta join,模拟真实 2-pattern join 场景。
     * 测试 rete 跨 pattern join 是否引入 super-linear scaling。
     */
    @Test
    @DisplayName("dual class Person+Address + 1 rule, N fact scaling")
    void dualClassScaling() {
        Rule r = buildRule2Pattern("R1", "Person", "name", "alice", "Address", "street", "main");
        ResourceLibrary rl = buildResourceLibrary2Class();
        KnowledgePackage kp = buildKp(r, rl);

        for (int n : SCALING_N) {
            runScalingN(kp, n, /*dualClass=*/true);
        }
    }

    // ====== helpers ======

    private void runScalingN(KnowledgePackage kp, int n, boolean dualClass) {
        String label = dualClass ? "dual" : "single";
        int warmup = 3;
        int iters = 5;
        int prevFired = -1;

        for (int i = 0; i < warmup; i++) {
            int fired = run(kp, n, dualClass);
            if (prevFired < 0) prevFired = fired;
        }

        long[] times = new long[iters];
        int fired = -1;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            fired = run(kp, n, dualClass);
            long t1 = System.nanoTime();
            times[i] = t1 - t0;
        }

        Arrays.sort(times);
        long medianNs = times[times.length / 2];
        double perFactUs = (medianNs / 1e3) / (n * (dualClass ? 2 : 1));
        double totalMs = medianNs / 1e6;
        System.out.printf(
            "[V5.85 PerfScaling] %s | N=%-5d | fired=%d (warmup=%d) | total=%.2fms per-fact=%.2fus%n",
            label, n, fired, prevFired, totalMs, perFactUs);
        assertEquals(prevFired, fired,
            label + " N=" + n + ": warmup vs measurement fired 应一致(确定性 bench)");
    }

    private int run(KnowledgePackage kp, int n, boolean dualClass) {
        KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
        for (int i = 0; i < n; i++) {
            if (dualClass) {
                session.insert(new Address(UUID.randomUUID().toString()));
                session.insert(new Person(UUID.randomUUID().toString()));
            } else {
                session.insert(new Person(UUID.randomUUID().toString()));
            }
        }
        RuleExecutionResponse resp = session.fireRules();
        return resp.getFiredRules() == null ? 0 : resp.getFiredRules().size();
    }

    private Rule buildRule(String name, String varCat, String varName, String value) {
        Rule r = new Rule();
        r.setName(name); r.setSalience(0);
        And and = new And();
        and.addCriterion(buildCriteria(varCat, varName, value));
        Lhs lhs = new Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);
        return r;
    }

    private Rule buildRule2Pattern(String name, String cat1, String field1, String val1,
                                    String cat2, String field2, String val2) {
        Rule r = new Rule();
        r.setName(name); r.setSalience(0);
        And and = new And();
        and.addCriterion(buildCriteria(cat1, field1, val1));
        and.addCriterion(buildCriteria(cat2, field2, val2));
        Lhs lhs = new Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);
        return r;
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

    private ResourceLibrary buildResourceLibrary(String catName, String... fieldNames) {
        VariableLibrary lib = new VariableLibrary();
        VariableCategory cat = new VariableCategory();
        cat.setName(catName);
        cat.setType(CategoryType.Clazz);
        cat.setClazz(Person.class.getName());
        List<Variable> vars = new ArrayList<>();
        for (String fn : fieldNames) {
            Variable v = new Variable();
            v.setName(fn); v.setLabel(fn); v.setType(Datatype.String); v.setAct(Act.In);
            vars.add(v);
        }
        cat.setVariables(vars);
        lib.addVariableCategory(cat);
        return new ResourceLibrary(Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());
    }

    private ResourceLibrary buildResourceLibrary2Class() {
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

    private KnowledgePackage buildKp(Rule r, ResourceLibrary rl) {
        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
        return new KnowledgeBase(rete).getKnowledgePackage();
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
