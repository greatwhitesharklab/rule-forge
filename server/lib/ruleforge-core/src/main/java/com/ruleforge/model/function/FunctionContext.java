package com.ruleforge.model.function;

import com.ruleforge.model.rule.RuleInfo;

import java.util.Map;

/**
 * 函数执行所需最小上下文:替代 {@code com.ruleforge.runtime.WorkingMemory},让
 * {@link FunctionDescriptor} 不再 import runtime(分层单向修复,TD-2.3)。
 *
 * <p>由 runtime 侧提供适配器(典型实现:包 {@code WorkingMemory} 的 adapter),
 * 实现方调用方只传 FunctionContext,不接触 WorkingMemory。
 */
public interface FunctionContext {
    /** 当前正在求值的规则(用于 ActiveRule 取 activationGroup)。 */
    RuleInfo getCurrentRule();

    /** 激活指定 activation group 中的指定规则名。 */
    void activeRule(String activationGroup, String ruleName);

    /** 激活指定 agenda group。 */
    void activeAgendaGroup(String groupName);

    /** 更新工作区对象(返回是否成功)。 */
    boolean update(Object fact);

    /** 当前参数 Map(用于 UpdateFact/UpdateParameter)。 */
    Map<String, Object> getParameters();
}
