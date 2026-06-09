package com.ruleforge.runtime.rete;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.debug.MsgType;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.WorkingMemory;
import com.ruleforge.runtime.assertor.AssertorEvaluator;
import org.springframework.context.ApplicationContext;

import java.util.List;

public interface Context {

    void addTipMsg(String var1);

    String getTipMsg();

    void cleanTipMsg();

    AssertorEvaluator getAssertorEvaluator();

    ValueCompute getValueCompute();

    ApplicationContext getApplicationContext();

    String getVariableCategoryClass(String var1);

    WorkingMemory getWorkingMemory();

    Object parseExpression(String var1);

    List<MessageItem> getExecuteMessageItems();

    void logMsg(String msg, MsgType msgType);

    void logMsg(String msg, MsgType msgType, String leftVariable, String leftVariableValue, String rightVariable, String rightVariableValue);

    RuleInfo getCurrentRule();
}
