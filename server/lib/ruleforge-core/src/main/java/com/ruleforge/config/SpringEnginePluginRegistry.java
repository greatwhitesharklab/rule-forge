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
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationContext;

import java.util.Collection;
import java.util.Collections;

/**
 * {@link EnginePluginRegistry} 的 Spring 实现 —— {@code ruleforge-core} 内唯一接触
 * {@link ApplicationContext} 的类(CLAUDE.md "核心不渗 Spring" 的 sanctioned 触点)。
 *
 * <p>V6.13.4e: 收口 V6.13.4 系列 —— 去 {@code ApplicationContextAware} +
 * {@code setApplicationContext} 回调,改构造注入 {@link ApplicationContext}(跟
 * V6.13.4a 5 个 core Aware 类同模式)。V6.13.4b 的 {@code BeanFactory} 过渡字段
 * 一并移除:ctor 直接持 ctx,ctx 本身即 {@code BeanFactory},lazy {@code getBean}
 * 改回 {@code applicationContext.getBean},不再需要额外 {@code BeanFactory} 抽象。
 *
 * <p>装配:{@code ruleforge-core-context.xml} 的 {@code <bean id="ruleforge.pluginRegistry">}
 * 加 {@code autowire="constructor"},Spring 按类型把当前 ApplicationContext 注入 ctor。
 * 9 个 {@code getBeansOfType} + {@link EngineContext#init} + {@link Splash} 从
 * {@code setApplicationContext} 搬到 {@link #init()} ({@code @PostConstruct}) —— 时机等价
 * (bean 构造 + 注入完成后、bean 使用前),避免在 ctor 里触发其他 bean 提前实例化的循环依赖风险。
 *
 * <p>注:本类 {@code import org.springframework.context.ApplicationContext} 是有意保留的
 * sanctioned 依赖 —— 引擎**逻辑**(model/runtime/parse/ir,365 文件)0 Spring import,
 * 全部走 {@link EnginePluginRegistry} 接口 + {@link EngineContext} 静态桥;只有本装配类
 * 接触 Spring。"去 setApplicationContext" 去的是 Aware 回调反模式,**不是**去掉 Spring 装配
 * (那是另一个目标,需要 SPI/ServiceLoader 替代,目前无此需求)。
 */
public class SpringEnginePluginRegistry implements EnginePluginRegistry {

    private Collection<Assertor> assertors = Collections.emptyList();
    private Collection<CriterionParser> criterionParsers = Collections.emptyList();
    private Collection<ActionParser> actionParsers = Collections.emptyList();
    private Collection<CriterionBuilder> criterionBuilders = Collections.emptyList();
    private Collection<ResourceBuilder> resourceBuilders = Collections.emptyList();
    private Collection<ResourceProvider> resourceProviders = Collections.emptyList();
    private Collection<BsfVariableProvider> bsfVariableProviders = Collections.emptyList();
    private Collection<FunctionDescriptor> functionDescriptors = Collections.emptyList();
    private Collection<DebugWriter> debugWriters = Collections.emptyList();

    private final ApplicationContext applicationContext;

    public SpringEnginePluginRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * V6.13.4e: 原 {@code setApplicationContext} 职责搬迁到此。{@code @PostConstruct}
     * 在 ctor + 依赖注入完成后调用,等价于原 Aware 回调时机;9 个 {@code getBeansOfType}
     * 在此触发各类 plugin bean 实例化(若未实例化),{@link EngineContext#init} 把本 registry
     * 注入静态桥供深调用点取用。
     */
    @PostConstruct
    void init() {
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

    /** 懒查(单例);不在 init() 里 eager getBean,避免与注入 registry 的 bean 形成初始化环。 */
    @Override
    public AssertorEvaluator getAssertorEvaluator() {
        return applicationContext.getBean("ruleforge.assertorEvaluator", AssertorEvaluator.class);
    }

    @Override
    public ValueCompute getValueCompute() {
        return applicationContext.getBean("ruleforge.valueCompute", ValueCompute.class);
    }

    @Override
    public Object getBean(String beanId) {
        return applicationContext.getBean(beanId);
    }
}
