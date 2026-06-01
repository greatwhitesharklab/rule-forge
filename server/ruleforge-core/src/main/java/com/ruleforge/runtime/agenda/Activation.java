package com.ruleforge.runtime.agenda;

import com.ruleforge.action.ActionValue;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.rete.Context;

import java.util.List;

public interface Activation extends Comparable<Activation> {
    boolean isProcessed();

    Rule getRule();

    boolean contain(Object var1);

    RuleInfo execute(Context var1, List<RuleInfo> var2, List<ActionValue> var3);
}
