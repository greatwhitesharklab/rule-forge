package com.ruleforge.runtime;

import com.ruleforge.debug.DebugWriter;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.function.FunctionDescriptor;
import com.ruleforge.plugin.EnginePluginRegistry;
import com.ruleforge.runtime.assertor.AssertorEvaluator;
import com.ruleforge.runtime.rete.ValueCompute;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 引擎运行时静态桥:给非 Spring 管理的深调用点提供 Spring 收集的插件与 bean。
 *
 * <p>深调用点 —— {@code KnowledgeSessionFactory.new KnowledgeSessionImpl}、
 * {@code ValueCompute} 求值时、模型对象 {@code CommonFunctionLeftPart} —— 不是 Spring bean,
 * 拿不到注入;它们经此静态桥取函数表 / debug writer / assertorEvaluator / valueCompute。
 *
 * <p>由 {@code SpringEnginePluginRegistry} 在 setApplicationContext 时经
 * {@link #init(EnginePluginRegistry)} 注入。这是 ruleforge-core 内继 {@code config/} 之外
 * 另一个 sanctioned 的 Spring 间接触点(深调用点 DI 不可行时的务实桥,
 * CLAUDE.md "核心不渗 Spring" 的已记录例外)。
 */
public final class EngineContext {
    private static volatile EnginePluginRegistry registry;
    private static volatile Map<String, FunctionDescriptor> functionDescriptorMap = Collections.emptyMap();
    private static volatile Map<String, FunctionDescriptor> functionDescriptorLabelMap = Collections.emptyMap();

    private EngineContext() {
    }

    /** 由 SpringEnginePluginRegistry 调用;构建函数名/label 索引(含去重 + disabled 过滤)。 */
    public static void init(EnginePluginRegistry r) {
        registry = r;
        Map<String, FunctionDescriptor> byName = new HashMap<>();
        Map<String, FunctionDescriptor> byLabel = new HashMap<>();
        for (FunctionDescriptor fun : r.getFunctionDescriptors()) {
            if (fun.isDisabled()) {
                continue;
            }
            if (byName.containsKey(fun.getName())) {
                throw new RuntimeException("Duplicate function [" + fun.getName() + "]");
            }
            byName.put(fun.getName(), fun);
            byLabel.put(fun.getLabel(), fun);
        }
        functionDescriptorMap = byName;
        functionDescriptorLabelMap = byLabel;
    }

    public static AssertorEvaluator getAssertorEvaluator() {
        return registry.getAssertorEvaluator();
    }

    public static ValueCompute getValueCompute() {
        return registry.getValueCompute();
    }

    public static Collection<DebugWriter> getDebugWriters() {
        return registry.getDebugWriters();
    }

    /** 按 bean id 动态查找(ExecuteMethodAction / ScoreRule 规则配置驱动的动态 bean 引用)。 */
    public static Object getBean(String beanId) {
        return registry.getBean(beanId);
    }

    public static FunctionDescriptor findFunctionDescriptor(String name) {
        FunctionDescriptor fun = functionDescriptorMap.get(name);
        if (fun == null) {
            throw new RuleException("Function[" + name + "] not exist.");
        }
        return fun;
    }

    public static Map<String, FunctionDescriptor> getFunctionDescriptorMap() {
        return functionDescriptorMap;
    }

    public static Map<String, FunctionDescriptor> getFunctionDescriptorLabelMap() {
        return functionDescriptorLabelMap;
    }
}
