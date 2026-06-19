package com.ruleforge.runtime.agenda;

import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.engine.KnowledgeSession;
import com.ruleforge.runtime.event.impl.ActivationCancelledEventImpl;
import com.ruleforge.engine.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractRuleBox implements RuleBox {
    protected List<RuleInfo> executedRules;
    protected Context context;
    protected List<Rule> rules;

    public AbstractRuleBox(Context context, List<RuleInfo> executedRules) {
        this.context = context;
        this.rules = new ArrayList();
        this.executedRules = executedRules;
    }

    protected void retract(Object obj, List<Activation> activations) {
        List<Activation> needRemovedList = new ArrayList();
        // V5.96 — Iterator var123 → enhanced for
        for (Activation activation : activations) {
            if (activation.contain(obj)) {
                needRemovedList.add(activation);
            }
        }

        KnowledgeSession session = (KnowledgeSession) this.context.getWorkingMemory();
        // V5.96 — Iterator var123 → enhanced for
        for (Activation ac : needRemovedList) {
            activations.remove(ac);
            session.fireEvent(new ActivationCancelledEventImpl(ac, session));
        }

    }

    protected boolean addActivation(Activation activation, List<Activation> list) {
        boolean result = list.add(activation);
        Collections.sort(list);
        return result;
    }

    protected boolean activationShouldAdd(Activation activation) {
        Rule rule = activation.getRule();
        // V5.96 — Iterator var123 → for-each with predicate
        Rule r = null;
        for (Rule candidate : this.rules) {
            if (candidate.equals(rule)) {
                r = candidate;
                break;
            }
        }
        if (r == null) {
            return true;
        }

        if (r.getLoop() != null && r.getLoop()) {
            return true;
        } else {
            return false;
        }
    }

    public List<Rule> getRules() {
        return this.rules;
    }
}
