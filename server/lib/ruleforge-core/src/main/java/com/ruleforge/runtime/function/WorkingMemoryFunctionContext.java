package com.ruleforge.runtime.function;

import com.ruleforge.model.function.FunctionContext;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.WorkingMemory;

import java.util.Map;

/**
 * {@link FunctionContext} 的 runtime 适配:把 {@link WorkingMemory} 的 5 个方法
 * 投影为函数执行所需最小上下文。
 *
 * <p>V5.76.7 TD-2.3:让 FunctionDescriptor 不再 import WorkingMemory,
 * runtime 侧只在调用现场建一次 adapter。
 */
public class WorkingMemoryFunctionContext implements FunctionContext {
    private final WorkingMemory wm;

    public WorkingMemoryFunctionContext(WorkingMemory wm) {
        this.wm = wm;
    }

    @Override
    public RuleInfo getCurrentRule() {
        return wm.getContext().getCurrentRule();
    }

    @Override
    public void activeRule(String activationGroup, String ruleName) {
        wm.activeRule(activationGroup, ruleName);
    }

    @Override
    public void activeAgendaGroup(String groupName) {
        wm.activeAgendaGroup(groupName);
    }

    @Override
    public boolean update(Object fact) {
        return wm.update(fact);
    }

    @Override
    public Map<String, Object> getParameters() {
        return wm.getParameters();
    }
}
