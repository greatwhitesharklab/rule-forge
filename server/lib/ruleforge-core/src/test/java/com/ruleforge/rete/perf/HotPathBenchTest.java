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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.87 — long-running hot path bench,给 JFR attach 提供 30s+ 持续 workload。
 *
 * <p>前序 phase(V5.85 perf scaling, V5.86 findObject classNameCache)都用 wall-time scaling
 * 反推 perf 模型,无法精确定位 per-fact 内部 hot method 占比。本 bench 是 V5.87 第一步
 * — 提供 long-running rete workload,让 JFR / async-profiler 能采到 30s+ CPU sample 拿
 * 火焰图。
 *
 * <p><b>跑法</b>:
 * <pre>
 * mvn test -pl lib/ruleforge-core \
 *   -Dtest=HotPathBenchTest \
 *   -DargLine="-XX:StartFlightRecording=duration=30s,filename=target/v587.jfr,settings=profile"
 * jfr summary target/v587.jfr
 * </pre>
 *
 * <p>workload:基于 EvalBenchmarkV579 no_eval,1 dual class rule,N fact insert,持续 30s+
 * 重复跑直到 30s 截止。JFR 自动在 duration 结束时 dump recording。
 *
 * <p>不动 production code,无 assert(只跑 workload)。配合 [[v585-perf-scaling-analysis]] +
 * [[v586-findobject-classcache]] 累计数据,定 V5.88+ 真正优化点。
 *
 * @since 5.87
 */
@Tag("perf")
@DisplayName("V5.87 — long-running hot path bench (JFR 30s+ 持续 workload)")
class HotPathBenchTest {

    /** 每次 insert batch 大小 */
    private static final int BATCH = 2000;
    /** 跑多久停(JFR duration 是 30s,本 bench 跑 35s 保证 JFR 拿全部) */
    private static final long DURATION_NANOS = 35L * 1_000_000_000L;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @Test
    @DisplayName("dual class hot path 35s long-running workload(JFR attach 目标)")
    void longRunningHotPath() {
        // 1 dual class rule:Person(name=alice) AND Address(street=main)
        Rule r = buildDualClassRule("alice", "main");
        KnowledgePackage kp = buildKp(r);

        long t0 = System.nanoTime();
        long elapsed = 0;
        int totalIters = 0;
        int totalFacts = 0;
        while (elapsed < DURATION_NANOS) {
            // 每次 run:insert BATCH fact + fireRules
            KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
            for (int i = 0; i < BATCH; i++) {
                session.insert(new Person(UUID.randomUUID().toString()));
                session.insert(new Address(UUID.randomUUID().toString()));
            }
            // 1 个 alice + 1 个 main 混入,期望 fired=1
            session.insert(new Person("alice"));
            session.insert(new Address("main"));
            assertNotNull(session.fireRules());
            totalIters++;
            totalFacts += (BATCH * 2 + 2);
            elapsed = System.nanoTime() - t0;
        }

        double totalSeconds = elapsed / 1e9;
        double perRunMs = (elapsed / 1e6) / totalIters;
        double perFactUs = (elapsed / 1e3) / totalFacts;
        System.out.printf(
            "[V5.87 HotPathBench] duration=%.1fs | iters=%d | facts=%d | per-run=%.2fms per-fact=%.2fus%n",
            totalSeconds, totalIters, totalFacts, perRunMs, perFactUs);
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
        public Address(String street) { this.street = street; }
        public String getStreet() { return street; }
    }
}
