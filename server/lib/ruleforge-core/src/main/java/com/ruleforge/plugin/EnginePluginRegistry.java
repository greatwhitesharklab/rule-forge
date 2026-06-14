package com.ruleforge.plugin;

import com.ruleforge.action.BsfVariableProvider;
import com.ruleforge.builder.resource.ResourceBuilder;
import com.ruleforge.builder.resource.ResourceProvider;
import com.ruleforge.debug.DebugWriter;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.model.rete.builder.CriterionBuilder;
import com.ruleforge.parse.ActionParser;
import com.ruleforge.parse.CriterionParser;
import com.ruleforge.runtime.assertor.Assertor;

import java.util.Collection;

/**
 * 引擎插件注册表(SPI)。引擎逻辑只依赖此接口,不依赖 Spring。
 *
 * <p>实现 {@code com.ruleforge.config.SpringEnginePluginRegistry} 由 Spring 装配,
 * 通过 {@link org.springframework.context.ApplicationContextAware} 收集所有插件 bean —— 这是
 * {@code ruleforge-core} 内唯一 sanctioned 的 Spring 触点(CLAUDE.md "核心不渗 Spring")。
 *
 * <p>历史:原 15 个 {@code ApplicationContextAware} 类各自 {@code getBeansOfType(X)} 收集插件,
 * 现集中到此一处,引擎类改为注入本接口。
 */
public interface EnginePluginRegistry {

    Collection<Assertor> getAssertors();

    Collection<CriterionParser> getCriterionParsers();

    Collection<ActionParser> getActionParsers();

    Collection<CriterionBuilder> getCriterionBuilders();

    Collection<ResourceBuilder> getResourceBuilders();

    Collection<ResourceProvider> getResourceProviders();

    Collection<BsfVariableProvider> getBsfVariableProviders();

    Collection<FunctionDescriptor> getFunctionDescriptors();

    Collection<DebugWriter> getDebugWriters();

    /**
     * 按 bean id 动态查找(给 ExecuteMethodAction / ScoreRule 这类规则配置驱动的动态 bean 引用兜底)。
     * 静态已知的 bean 优先用构造注入,不用此方法。
     */
    Object getBean(String beanId);
}
