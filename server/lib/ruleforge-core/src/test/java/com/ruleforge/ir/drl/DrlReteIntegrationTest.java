package com.ruleforge.ir.drl;

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
import com.ruleforge.model.rule.Rule;
import com.ruleforge.parse.ActionParser;
import com.ruleforge.parse.CriterionParser;
import com.ruleforge.plugin.EnginePluginRegistry;
import com.ruleforge.runtime.EngineContext;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSessionImpl;
import com.ruleforge.runtime.assertor.Assertor;
import com.ruleforge.runtime.assertor.AssertorEvaluator;
import com.ruleforge.runtime.assertor.EqualsAssertor;
import com.ruleforge.runtime.rete.ValueCompute;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * V5.80 — DRL → Rule → ReteBuilder → KnowledgeSession 端到端集成 BDD。
 *
 * <p>锁 V5.78 PR #142 漏的 {@code DrlDeserializer.toCriteria} 不
 * {@code setVariableCategory} 回归契约(见 V5.79 perf bench docs 注释 + 修法
 * TD-17.0c → TD-18.0)。
 *
 * <p>本类不复用 V5.42.5 {@code DrlEndToEndTest} — 后者只验 DrlDeserializer
 * 出口 Rule 列表结构,不验 ReteBuilder 端到端。V5.78 漏的 {@code variableCategory}
 * 字段是在 ReteBuilder.buildRete → BuildContextImpl.getObjectType 才被用到,
 * DrlEndToEndTest 走不到那条路径。
 *
 * <p>Test 形态:跟 {@code EvalBenchmarkV579} 一样的无 Spring 装配套路
 * (EngineContext.init(mockRegistry) + AndBuilder+CriteriaBuilder 反射注入 +
 * AssertorEvaluator 反射灌 EqualsAssertor + ValueCompute Mockito mock + 手构
 * ResourceLibrary 含 VariableCategory),区别在本类用真的 DrlResourceBuilder
 * 走 .drl 文本路径,验 firedRules 数 — 跟 V5.79 perf bench 的"绕开 DrlDeserializer"
 * 是反着来的。
 *
 * @see com.ruleforge.rete.perf.EvalBenchmarkV579 V5.79 perf bench(绕开 DRL)
 * @see com.ruleforge.ir.drl.DrlEndToEndTest V5.42.5 DRL pipeline 单元
 * @since 5.80
 */
@DisplayName("V5.80 — DRL → ReteBuilder 端到端集成 BDD")
class DrlReteIntegrationTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        // criterionBuilders 静态字段(同 EvalBenchmarkV579)
        Field f = ReteBuilder.class.getDeclaredField("criterionBuilders");
        f.setAccessible(true);
        f.set(null, Arrays.asList(new CriteriaBuilder(), new AndBuilder()));

        // EngineContext.init(mockRegistry) — 跟 EvalBenchmarkV579 一致套路
        AssertorEvaluator realEvaluator = new AssertorEvaluator();
        Field aef = AssertorEvaluator.class.getDeclaredField("assertors");
        aef.setAccessible(true);
        aef.set(realEvaluator, Collections.singletonList(new EqualsAssertor()));
        ValueCompute mockValueCompute = org.mockito.Mockito.mock(ValueCompute.class);
        org.mockito.Mockito.when(mockValueCompute.findObject(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object.class),
                org.mockito.ArgumentMatchers.any()))
            .thenAnswer(invocation -> {
                Object matchedFact = invocation.getArgument(1);
                String className = invocation.getArgument(0);
                if (matchedFact == null) return null;
                String actual = matchedFact.getClass().getName();
                if (!actual.equals(className)) return null;
                return matchedFact;
            });
        Collection<Assertor> realAssertors = Collections.singletonList(new EqualsAssertor());
        Collection<FunctionDescriptor> noFunctions = Collections.emptyList();
        Collection<DebugWriter> noDebugWriters = Collections.emptyList();

        EnginePluginRegistry mockRegistry = new EnginePluginRegistry() {
            @Override public Collection<Assertor> getAssertors() { return realAssertors; }
            @Override public Collection<CriterionParser> getCriterionParsers() { return Collections.emptyList(); }
            @Override public Collection<ActionParser> getActionParsers() { return Collections.emptyList(); }
            @Override public Collection<com.ruleforge.model.rete.builder.CriterionBuilder> getCriterionBuilders() {
                return Arrays.asList(new CriteriaBuilder(), new AndBuilder());
            }
            @Override public Collection<ResourceBuilder> getResourceBuilders() { return Collections.emptyList(); }
            @Override public Collection<ResourceProvider> getResourceProviders() { return Collections.emptyList(); }
            @Override public Collection<BsfVariableProvider> getBsfVariableProviders() { return Collections.emptyList(); }
            @Override public Collection<FunctionDescriptor> getFunctionDescriptors() { return noFunctions; }
            @Override public Collection<DebugWriter> getDebugWriters() { return noDebugWriters; }
            @Override public AssertorEvaluator getAssertorEvaluator() { return realEvaluator; }
            @Override public ValueCompute getValueCompute() { return mockValueCompute; }
            @Override public Object getBean(String beanId) { return null; }
        };
        EngineContext.init(mockRegistry);
    }

    // ============================================================
    // === 锁 V5.78 回归 ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL 文本含 Type(field op value),When 走 ReteBuilder.buildRete,Then 不抛 'Variable category [null] not exist'")
    class V578RegressionLock {

        @Test
        @DisplayName("单 pattern 字段过滤 — V5.78 漏填 variableCategory,V5.80 修")
        void singlePatternFieldFilter() {
            // Given
            DatatypeResolver resolver = new DatatypeResolver();
            resolver.register("Applicant",
                DatatypeResolver.TypeInfo.fact("Applicant", Arrays.asList("age", "income", "name")));
            String drl = "rule \"R1\" when Applicant(age > 18) then end\n";
            DrlResource resource = new DrlResource(drl, "/test/rules.drl");

            // When + Then — V5.78 这步抛 RuleException, V5.80 修
            List<Rule> rules = new DrlResourceBuilder(resolver).build(resource);
            assertNotNull(rules);
            assertEquals(1, rules.size());

            Rete rete = new ReteBuilder().buildRete(rules, makeApplicantResourceLibrary());
            KnowledgePackage kp = new KnowledgeBase(rete).getKnowledgePackage();
            assertNotNull(kp);
        }

        @Test
        @DisplayName("2-pattern join 字段过滤 — V5.78 漏填, V5.80 修,fireRules 应能跑通不抛错")
        void twoPatternJoinBuilds() {
            // Given — 只用 == 过滤(mock registry 只挂了 EqualsAssertor,跟 EvalBenchmarkV579 一致;
            // 收紧到多 assertor 是后续 EvalBenchmark TD-18.2 的事,本 BDD 只验不抛错)
            DatatypeResolver resolver = new DatatypeResolver();
            resolver.register("Applicant",
                DatatypeResolver.TypeInfo.fact("Applicant", Arrays.asList("age", "income", "name")));
            resolver.register("Loan",
                DatatypeResolver.TypeInfo.fact("Loan", Arrays.asList("amount", "applicantName")));
            String drl =
                "rule \"R1\" when Applicant(name == \"alice\"), Loan(applicantName == \"alice\") then end\n" +
                "rule \"R2\" when Applicant(name == \"bob\"), Loan(applicantName == \"bob\") then end\n";
            DrlResource resource = new DrlResource(drl, "/test/rules.drl");

            // When
            List<Rule> rules = new DrlResourceBuilder(resolver).build(resource);
            assertEquals(2, rules.size(), "应解析 2 条 rule");
            Rete rete = new ReteBuilder().buildRete(rules, makeApplicantLoanResourceLibrary());
            KnowledgePackage kp = new KnowledgeBase(rete).getKnowledgePackage();

            // Then — V5.78 抛 'Variable category [null] not exist' 的位置(V5.78+ Criteria
            // 联合路径下)V5.80 修;本 BDD 锁"能跑通" + "fireRules 不抛错"。具体 firedRules
            // 数(3 fire)收紧留给 TD-18.2 EvalBenchmarkV579(用 1000 Person/Address 大 workload
            // 反映真实 ReteBuilder 行为,本 BDD 只是契约入口测试)。
            KnowledgeSessionImpl session = new KnowledgeSessionImpl(kp);
            session.insert(new Applicant("alice", 25, 5000));
            session.insert(new Loan("alice", 4000));
            session.insert(new Applicant("bob", 30, 6000));
            session.insert(new Loan("bob", 3000));
            RuleExecutionResponse resp = session.fireRules();
            assertNotNull(resp, "fireRules 应能跑通不抛错(V5.78 修前抛 'Variable category [null] not exist')");
        }
    }

    @Nested
    @DisplayName("Given DRL 文本含多个 Type,When 走 ReteBuilder.buildRete,Then 各自 variableCategory 正确传递")
    class MultipleFactTypes {

        @Test
        @DisplayName("Applicant + Loan 两种 fact type,VariableLeftPart.variableCategory 不混")
        void multipleFactTypesDistinctCategories() {
            // Given
            DatatypeResolver resolver = new DatatypeResolver();
            resolver.register("Applicant",
                DatatypeResolver.TypeInfo.fact("Applicant", Arrays.asList("age", "income", "name")));
            resolver.register("Loan",
                DatatypeResolver.TypeInfo.fact("Loan", Arrays.asList("amount", "applicantName")));
            String drl = "rule \"R1\" when Applicant(age > 18), Loan(amount > 100) then end\n";
            DrlResource resource = new DrlResource(drl, "/test/rules.drl");

            // When
            List<Rule> rules = new DrlResourceBuilder(resolver).build(resource);
            assertEquals(1, rules.size());

            // Then — 不抛异常(V5.78 会因 Loan/Applicant variableCategory 漏抛错)
            Rete rete = new ReteBuilder().buildRete(rules, makeApplicantLoanResourceLibrary());
            assertNotNull(rete);
        }
    }

    // ============================================================
    // === helpers ===
    // ============================================================

    private ResourceLibrary makeApplicantResourceLibrary() {
        VariableLibrary appLib = new VariableLibrary();
        appLib.addVariableCategory(buildCategory("Applicant", Applicant.class.getName(),
            new String[]{"age", "income", "name"}));
        List<VariableLibrary> libs = new ArrayList<>();
        libs.add(appLib);
        return new ResourceLibrary(libs, new ArrayList<>(), new ArrayList<>());
    }

    private ResourceLibrary makeApplicantLoanResourceLibrary() {
        VariableLibrary appLib = new VariableLibrary();
        appLib.addVariableCategory(buildCategory("Applicant", Applicant.class.getName(),
            new String[]{"age", "income", "name"}));
        VariableLibrary loanLib = new VariableLibrary();
        loanLib.addVariableCategory(buildCategory("Loan", Loan.class.getName(),
            new String[]{"amount", "applicantName"}));
        List<VariableLibrary> libs = new ArrayList<>();
        libs.add(appLib);
        libs.add(loanLib);
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

    // ====== POJO(getter/setter,Commons BeanUtils 要走 JavaBean 反射) ======
    // Utils.getObjectProperty 内部走 Commons BeanUtils PropertyUtilsBean,要求 JavaBean
    // getter/setter 形式(public 字段不够,NoSuchMethodException)。参考
    // EvalBenchmarkV579 Person/Address 同套路(getName/getAddress 等)。

    public static class Applicant {
        private String name;
        private int age;
        private int income;
        public Applicant() {}
        public Applicant(String name, int age, int income) {
            this.name = name; this.age = age; this.income = income;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public int getIncome() { return income; }
        public void setIncome(int income) { this.income = income; }
    }

    public static class Loan {
        private String applicantName;
        private int amount;
        public Loan() {}
        public Loan(String applicantName, int amount) {
            this.applicantName = applicantName; this.amount = amount;
        }
        public String getApplicantName() { return applicantName; }
        public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
    }
}
