package com.ruleforge.runtime.agenda;

import com.ruleforge.action.ActionValue;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleInfo;

import java.util.List;

public interface RuleBox {

    List<RuleInfo> execute(AgendaFilter filter, int max, List<ActionValue> actionValues);

    boolean add(Activation activation);

    RuleBox next();

    List<Rule> getRules();

    void retract(Object obj);

    void clean();
}
