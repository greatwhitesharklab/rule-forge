package com.ruleforge.runtime.agenda;

import java.util.ArrayList;
import java.util.List;

import com.ruleforge.action.ActionValue;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.rete.Context;

/**
 * @author Jacky.gao
 * 2015年1月2日
 */
public abstract class RuleGroup {
    private String name;
    protected List<RuleInfo> executedRules;
    protected List<Activation> activations = new ArrayList<Activation>();

    public RuleGroup(String name, List<RuleInfo> executedRules) {
        this.name = name;
        this.executedRules = executedRules;
    }

    public abstract List<RuleInfo> execute(Context context, AgendaFilter filter, int max, List<ActionValue> actionValues);

    public static Activation fetchNextExecutableActivation(List<Activation> activations) {
        Activation targetActivation = null;
        for (Activation ac : activations) {
            if (!ac.isProcessed()) {
                targetActivation = ac;
                break;
            }
        }
        return targetActivation;
    }

    public List<Activation> getActivations() {
        return activations;
    }

    public String getName() {
        return name;
    }

    public boolean contains(Rule rule) {
        for (Activation activation : activations) {
            if (activation.getRule().equals(rule)) {
                return true;
            }
        }
        return false;
    }
}
