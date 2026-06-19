package com.ruleforge.engine;
import java.util.List;
import com.ruleforge.debug.MessageItem;
import java.util.List;
import com.ruleforge.debug.MsgType;
import java.util.List;
import com.ruleforge.model.rule.RuleInfo;
import java.util.List;
import com.ruleforge.runtime.assertor.AssertorEvaluator;
import java.util.List;
import com.ruleforge.runtime.rete.ValueCompute;



public interface Context {

    void addTipMsg(String var1);

    String getTipMsg();

    void cleanTipMsg();

    AssertorEvaluator getAssertorEvaluator();

    ValueCompute getValueCompute();

    String getVariableCategoryClass(String var1);

    WorkingMemory getWorkingMemory();

    Object parseExpression(String var1);

    List<MessageItem> getExecuteMessageItems();

    void logMsg(String msg, MsgType msgType);

    void logMsg(String msg, MsgType msgType, String leftVariable, String leftVariableValue, String rightVariable, String rightVariableValue);

    RuleInfo getCurrentRule();
}
