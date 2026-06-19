package com.ruleforge.runtime.rete;
import com.ruleforge.engine.ValueCompute;
import com.ruleforge.engine.Context;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.debug.MsgType;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.ElCalculator;
import com.ruleforge.engine.EngineContext;
import com.ruleforge.engine.WorkingMemory;
import com.ruleforge.engine.AssertorEvaluator;
import org.apache.commons.lang.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContextImpl implements Context {
    private AssertorEvaluator assertorEvaluator;
    private Map<String, String> variableCategoryMap;
    private ValueCompute valueCompute;
    private WorkingMemory workingMemory;
    private List<MessageItem> executeMessageItems;
    private Rule currentRule;
    private StringBuilder tipMsgBuilder = new StringBuilder();

    public ContextImpl(WorkingMemory workingMemory, Map<String, String> variableCategoryMap, List<MessageItem> executeMessageItems) {
        this.workingMemory = workingMemory;
        this.assertorEvaluator = EngineContext.getAssertorEvaluator();
        this.variableCategoryMap = variableCategoryMap;
        this.executeMessageItems = executeMessageItems;
        this.valueCompute = EngineContext.getValueCompute();
    }

    public void addTipMsg(String msg) {
        if (this.tipMsgBuilder.length() > 0) {
            this.tipMsgBuilder.append(">>");
        }

        this.tipMsgBuilder.append(msg);
    }

    public void cleanTipMsg() {
        this.tipMsgBuilder.delete(0, this.tipMsgBuilder.length());
    }

    public String getTipMsg() {
        return this.tipMsgBuilder.length() > 0 ? this.tipMsgBuilder.toString() : null;
    }

    public WorkingMemory getWorkingMemory() {
        return this.workingMemory;
    }

    public AssertorEvaluator getAssertorEvaluator() {
        return this.assertorEvaluator;
    }

    public Object parseExpression(String expression) {
        //TODO 20210107 new calc
//        return (new ElCompute()).doCompute(expression);
        return (new ElCalculator()).eval(expression);
    }

    public void logMsg(String msg, MsgType type) {
        MessageItem item = new MessageItem(msg, type);
        this.executeMessageItems.add(item);
    }

    public void logMsg(String msg, MsgType type, String leftVariable, String leftVariableValue, String rightVariable, String rightVariableValue) {
        MessageItem item = new MessageItem(msg, type, leftVariable, leftVariableValue, rightVariable, rightVariableValue);
        this.executeMessageItems.add(item);
    }

    public List<MessageItem> getExecuteMessageItems() {
        return executeMessageItems;
    }

    public String getVariableCategoryClass(String variableCategory) {
        String clazz = this.variableCategoryMap.get(variableCategory);
        if (StringUtils.isEmpty(clazz)) {
            clazz = HashMap.class.getName();
        }

        return clazz;
    }

    public ValueCompute getValueCompute() {
        return this.valueCompute;
    }

    public void setCurrentRule(Rule currentRule) {
        this.currentRule = currentRule;
    }

    public RuleInfo getCurrentRule() {
        return this.currentRule;
    }
}
