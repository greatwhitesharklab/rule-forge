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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.9.18 — post-V6.9.17 perf baseline guardrail (P1 入口诊断)。
 *
 * <p>重用 V5.87 HotPathBenchTest workload (dual class rule:Person + Address,
 * N fact insert + 1 alice+main → fireRules),跑 5s 缩短版 (vs V5.87 35s long-running)
 * 配 {@code -P perf} profile 让 CI 不会跑。Assert per-fact wall-time 上界当
 * perf regression guardrail:
 * <ul>
 *   <li>上限 {@code 1.0us per-fact} — 比 V5.90 baseline 0.36us 宽,留 JIT warmup + CI
 *       噪音空间;真正的 V5.87/V5.99 baseline 0.10-0.23us 在 HotPathBenchTest 35s run 验证</li>
 *   <li>如果超过,说明有 perf regression 进了 main (例如新增 logMsg 没门控 / 新增
 *       double-lookup / 新增反编译 state machine)</li>
 * </ul>
 *
 * <p><b>完整 JFR profile 跑法</b> (跟 V5.87 一样, JFR attach 拿 30s+ 火焰图):
 * <pre>
 * mvn test -pl lib/ruleforge-core \
 *   -P perf \
 *   -Dtest=HotPathBenchTest \
 *   -DargLine="-XX:StartFlightRecording=duration=30s,filename=target/v6918.jfr,settings=profile"
 * jfr summary target/v6918.jfr
 * </pre>
 *
 * <p><b>预期 V6.9.18 JFR top hot method</b> (类比 V5.87 + V5.100 post-cleanup):
 * <ul>
 *   <li>AndActivity.passAndNode / CriteriaActivity.enter (rete 核心, JFR 一直 top)</li>
 *   <li>Long.getChars (FactIds bench 噪音, production 不付)</li>
 *   <li>HashMap.putAll (FactTracker.newSubFactTracker 防御性拷贝, V6.9.19+ 候选)</li>
 *   <li>ConcurrentHashMap.get (rete shared map, 待挖)</li>
 * </ul>
 *
 * <p><b>Why V6.9.18</b>: v69_pipeline P1 #8, P0 全收口 (V6.9.14-V6.9.17 4 PR)
 * 后需要新 perf baseline。 V5.100 后已 9+ PR 改动 (含 V6.6 FactStore.getAllFactsMap
 * 缓存 + V6.9.x logMsg 门控全 8 处), 火焰图可能变, 需要重 profile 找新 hot path
 * 给 V6.9.19+ 2-array merge / labeled loop 优化用。
 *
 * @see com.ruleforge.rete.perf.HotPathBenchTest V5.87 35s JFR 长跑版本
 * @since 6.9.18
 */
@Tag("perf")
@DisplayName("V6.9.18 — post-V6.9.17 perf baseline (5s shortened HotPath guardrail)")
class PostV617PerfBaselineTest {

    /** 每次 insert batch 大小 (跟 V5.87 HotPathBenchTest 一致) */
    private static final int BATCH = 2000;
    /** 跑多久停 (5s 缩短版, V5.87 是 35s 给 JFR) */
    private static final long DURATION_NANOS = 5L * 1_000_000_000L;
    /** Per-fact wall-time 上界 (V5.90 baseline 0.36us × 3 = 1.0us 留宽给 JIT warmup) */
    private static final double PER_FACT_US_CEILING = 1.0;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @Test
    @DisplayName("dual class hot path 5s — per-fact wall-time < 1.0us guardrail")
    void postV617PerfBaseline() {
        // 1 dual class rule: Person(name=alice) AND Address(street=main)
        Rule r = buildDualClassRule("alice", "main");
        KnowledgePackage kp = buildKp(r);

        long t0 = System.nanoTime();
        long elapsed = 0;
        int totalIters = 0;
        int totalFacts = 0;
        while (elapsed < DURATION_NANOS) {
            KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
            for (int i = 0; i < BATCH; i++) {
                session.insert(new Person(FactIds.next("p")));
                session.insert(new Address(FactIds.next("a")));
            }
            session.insert(new Person("alice"));
            session.insert(new Address("main"));
            session.fireRules();
            totalIters++;
            totalFacts += (BATCH * 2 + 2);
            elapsed = System.nanoTime() - t0;
        }

        double totalSeconds = elapsed / 1e9;
        double perRunMs = (elapsed / 1e6) / totalIters;
        double perFactUs = (elapsed / 1e3) / totalFacts;
        System.out.printf(
            "[V6.9.18 PostV617PerfBaseline] duration=%.1fs | iters=%d | facts=%d | per-run=%.2fms per-fact=%.3fus%n",
            totalSeconds, totalIters, totalFacts, perRunMs, perFactUs);

        // Guardrail: 如果 per-fact 超过上界, 说明有 perf regression
        assertThat(perFactUs)
            .as("V6.9.18 post-V6.9.17 perf guardrail — per-fact wall-time 应 < %.2fus, 实测 %.3fus",
                PER_FACT_US_CEILING, perFactUs)
            .isLessThan(PER_FACT_US_CEILING);
    }

    // ====== helpers (跟 V5.87 HotPathBenchTest 同步, 修改时两处一起改) ======

    private Rule buildDualClassRule(String personName, String street) {
        Rule r = new Rule();
        r.setName("R1"); r.setSalience(0);
        // V5.90 — 显式 debug=false 让 V5.88 早返 (CriteriaActivity.logMessage) 生效
        r.setDebug(false);
        And and = new And();
        and.addCriterion(buildCriteria("Person", "name", personName));
        and.addCriterion(buildCriteria("Address", "street", street));
        Lhs lhs = new Lhs();
        lhs.setCriterion(and);
        r.setLhs(lhs);
        return r;
    }

    private KnowledgePackage buildKp(Rule r) {
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
        ResourceLibrary rl = new ResourceLibrary(libs, new ArrayList<>(), new ArrayList<>());
        Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
        return new KnowledgeBase(rete).getKnowledgePackage();
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
        public Person addr;  // dummy for V5.99 reflection cache
        public Address(String street) { this.street = street; }
        public String getStreet() { return street; }
    }
}
