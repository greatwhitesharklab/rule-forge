package com.ruleforge.rete.test;

import com.ruleforge.action.BsfVariableProvider;
import com.ruleforge.builder.resource.ResourceBuilder;
import com.ruleforge.builder.resource.ResourceProvider;
import com.ruleforge.debug.DebugWriter;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.rete.builder.AndBuilder;
import com.ruleforge.model.rete.builder.CriteriaBuilder;
import com.ruleforge.model.rete.builder.ReteBuilder;
import com.ruleforge.parse.ActionParser;
import com.ruleforge.parse.CriterionParser;
import com.ruleforge.plugin.EnginePluginRegistry;
import com.ruleforge.runtime.EngineContext;
import com.ruleforge.runtime.assertor.Assertor;
import com.ruleforge.runtime.assertor.AssertorEvaluator;
import com.ruleforge.runtime.assertor.EqualsAssertor;
import com.ruleforge.runtime.rete.ValueCompute;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * V5.81 — 无 Spring EngineContext 装配 helper。
 *
 * <p>代替 {@code EvalBenchmarkV579} / {@code DrlReteIntegrationTest} 内重复写的
 * mock registry 套路。关键修复(V5.81):用**真实 ValueCompute 实例**,不 Mockito
 * mock — V5.79 perf bench 的 fired=0 bug 跟这里相关:Mockito mock 没 stub
 * {@code complexValueCompute},默认返 null,导致 {@code criteria.evaluate} 的
 * right side 永远是 null,equals(null) 永不命中,所有 rule 都不 fire(见
 * [[v580-drl-regression-fix]] TD-18.4 调查 trace)。
 *
 * <p>ValueCompute 本身是无状态 + public no-arg ctor 的纯函数类(见
 * {@code ValueCompute.java} line 38-44),直接 new 即可,无需 mock。
 * 只 stub {@code findObject}(Criteria.evaluate 用来在 fact className 命中时
 * 返 fact 自身,简化路径)。
 *
 * <p>本类由 {@code SingleRuleFiresBDD} + {@code EvalBenchmarkV579} 共享。
 *
 * @since 5.81
 */
public final class EngineContextWirer {

    private EngineContextWirer() {
    }

    /**
     * 装配 {@link EngineContext#init} 用的 mock registry + 反射注入 ReteBuilder
     * criterionBuilders。
     */
    public static void wire() throws Exception {
        // ReteBuilder 静态字段(V5.46 老 API 退化,生产 Spring 收集,本测试无 Spring 反射)
        Field f = ReteBuilder.class.getDeclaredField("criterionBuilders");
        f.setAccessible(true);
        f.set(null, Arrays.asList(new CriteriaBuilder(), new AndBuilder()));

        // 真实 ValueCompute(无状态,public ctor)
        ValueCompute realValueCompute = new ValueCompute();

        // 真实 AssertorEvaluator + 反射灌 EqualsAssertor(单 assertor 覆盖 ==)
        AssertorEvaluator realEvaluator = new AssertorEvaluator();
        Field aef = AssertorEvaluator.class.getDeclaredField("assertors");
        aef.setAccessible(true);
        aef.set(realEvaluator, Collections.singletonList(new EqualsAssertor()));

        Collection<Assertor> realAssertors = Collections.singletonList(new EqualsAssertor());
        Collection<FunctionDescriptor> noFunctions = Collections.emptyList();
        Collection<DebugWriter> noDebugWriters = Collections.emptyList();

        EnginePluginRegistry registry = new EnginePluginRegistry() {
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
            @Override public ValueCompute getValueCompute() { return realValueCompute; }
            @Override public Object getBean(String beanId) { return null; }
        };
        EngineContext.init(registry);
    }
}
