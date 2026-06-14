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
import com.ruleforge.runtime.assertor.Assertor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Collection;
import java.util.Collections;

/**
 * {@link EnginePluginRegistry} 的 Spring 实现 —— {@code ruleforge-core} 内唯一
 * {@link ApplicationContextAware} 类(CLAUDE.md "核心不渗 Spring" 的 sanctioned 触点)。
 *
 * <p>在 {@link #setApplicationContext} 里一次性收集所有插件 bean,引擎类通过接口注入取用,
 * 自身代码不再接触 {@code org.springframework}。
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

    @Override
    public Object getBean(String beanId) {
        return applicationContext.getBean(beanId);
    }
}
