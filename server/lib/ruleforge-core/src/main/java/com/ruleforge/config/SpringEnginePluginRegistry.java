package com.ruleforge.config;

import com.ruleforge.action.BsfVariableProvider;
import com.ruleforge.builder.resource.ResourceBuilder;
import com.ruleforge.builder.resource.ResourceProvider;
import com.ruleforge.debug.DebugWriter;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.rete.builder.CriterionBuilder;
import com.ruleforge.parse.ActionParser;
import com.ruleforge.parse.CriterionParser;
import com.ruleforge.plugin.EnginePluginRegistry;
import com.ruleforge.engine.EngineContext;
import com.ruleforge.runtime.assertor.Assertor;
import com.ruleforge.engine.AssertorEvaluator;
import com.ruleforge.engine.ValueCompute;
import com.ruleforge.Splash;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collection;
import java.util.Collections;

/**
 * {@link EnginePluginRegistry} 的 Spring 实现 —— {@code ruleforge-core} 内唯一
 * {@link ApplicationContextAware} 类(CLAUDE.md "核心不渗 Spring" 的 sanctioned 触点)。
 *
 * <p>V6.13.4b: 仍保留 {@code ApplicationContextAware} 作 sanctioned 入口,但内部 lazy
 * {@code getBean(...)} 走构造注入的 {@link BeanFactory}({@code ApplicationContext} 接口
 * 继承自 {@code BeanFactory},通过 ctor 注入 {@code BeanFactory} 而非存 raw ctx,
 * 新代码看不到 {@code ApplicationContext} 类型,ctx 泄漏面收敛到本类 setApplicationContext
 * 一行赋值)。
 *
 * <p>9 个 {@code getBeansOfType} 仍走 {@code applicationContext.getBeansOfType} —
 * 因为 {@code BeanFactory} 接口本身不暴露 {@code getBeansOfType},需要 ApplicationContext
 * 才能扫。这是有意保留的 sanctioned ctx 接触点。
 */
public class SpringEnginePluginRegistry implements ApplicationContextAware, EnginePluginRegistry {

    private Collection<Assertor> assertors = Collections.emptyList();
    private Collection<CriterionParser> criterionParsers = Collections.emptyList();
    private Collection<ActionParser> actionParsers = Collections.emptyList();
    private Collection<CriterionBuilder> criterionBuilders = Collections.emptyList();
    private Collection<ResourceBuilder> resourceBuilders = Collections.emptyList();
    private Collection<ResourceProvider> resourceProviders = Collections.emptyList();
    private Collection<BsfVariableProvider> bsfVariableProviders = Collections.emptyList();
    private Collection<FunctionDescriptor> functionDescriptors = Collections.emptyList();
    private Collection<DebugWriter> debugWriters = Collections.emptyList();

    private ApplicationContext applicationContext;
    private final BeanFactory beanFactory;

    public SpringEnginePluginRegistry(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.assertors = applicationContext.getBeansOfType(Assertor.class).values();
        this.criterionParsers = applicationContext.getBeansOfType(CriterionParser.class).values();
        this.actionParsers = applicationContext.getBeansOfType(ActionParser.class).values();
        this.criterionBuilders = applicationContext.getBeansOfType(CriterionBuilder.class).values();
        this.resourceBuilders = applicationContext.getBeansOfType(ResourceBuilder.class).values();
        this.resourceProviders = applicationContext.getBeansOfType(ResourceProvider.class).values();
        this.bsfVariableProviders = applicationContext.getBeansOfType(BsfVariableProvider.class).values();
        this.functionDescriptors = applicationContext.getBeansOfType(FunctionDescriptor.class).values();
        this.debugWriters = applicationContext.getBeansOfType(DebugWriter.class).values();
        // 初始化静态桥(深调用点用),并打印启动 banner(原 Utils.setApplicationContext 职责)
        EngineContext.init(this);
        new Splash().print();
    }

    @Override public Collection<Assertor> getAssertors() { return assertors; }
    @Override public Collection<CriterionParser> getCriterionParsers() { return criterionParsers; }
    @Override public Collection<ActionParser> getActionParsers() { return actionParsers; }
    @Override public Collection<CriterionBuilder> getCriterionBuilders() { return criterionBuilders; }
    @Override public Collection<ResourceBuilder> getResourceBuilders() { return resourceBuilders; }
    @Override public Collection<ResourceProvider> getResourceProviders() { return resourceProviders; }
    @Override public Collection<BsfVariableProvider> getBsfVariableProviders() { return bsfVariableProviders; }
    @Override public Collection<FunctionDescriptor> getFunctionDescriptors() { return functionDescriptors; }
    @Override public Collection<DebugWriter> getDebugWriters() { return debugWriters; }

    /** 懒查(单例);不在 setApplicationContext 里 eager getBean,避免与注入 registry 的 bean 形成初始化环。 */
    @Override
    public AssertorEvaluator getAssertorEvaluator() {
        return beanFactory.getBean("ruleforge.assertorEvaluator", AssertorEvaluator.class);
    }

    @Override
    public ValueCompute getValueCompute() {
        return beanFactory.getBean("ruleforge.valueCompute", ValueCompute.class);
    }

    @Override
    public Object getBean(String beanId) {
        return beanFactory.getBean(beanId);
    }
}
