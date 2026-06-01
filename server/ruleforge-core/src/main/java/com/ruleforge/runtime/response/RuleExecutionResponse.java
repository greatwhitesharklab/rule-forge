package com.ruleforge.runtime.response;

import com.ruleforge.action.ActionValue;
import com.ruleforge.model.rule.RuleInfo;

import java.util.List;

public interface RuleExecutionResponse extends ExecutionResponse {


    /**
     * @return 返回所有触发的规则对象信息
     */
    List<RuleInfo> getFiredRules();

    /**
     * @return 返回所有匹配的规则对象信息
     */
    List<RuleInfo> getMatchedRules();

    /**
     * @return 返回匹配规则动作执行时返回的结果(如果有定义返回值的话)
     */
    List<ActionValue> getActionValues();

    /**
     * @return 返回决策流执行信息
     */
    List<FlowExecutionResponse> getFlowExecutionResponses();
}
