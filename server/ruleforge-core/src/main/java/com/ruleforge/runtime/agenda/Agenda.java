package com.ruleforge.runtime.agenda;

import com.ruleforge.Utils;
import com.ruleforge.action.ActionValue;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.runtime.rete.Context;
import com.ruleforge.runtime.rete.FactTracker;
import com.ruleforge.runtime.rete.ReteInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Agenda {
    private Context context;
    private RuleBox ruleBox;
    private List<RuleInfo> matchedRules = new ArrayList<>();

    public Agenda(Context context) {
        this.context = context;
        this.ruleBox = new ActivationRuleBox(context, this.matchedRules);
    }

    public RuleExecutionResponse execute(AgendaFilter filter, int max) {
        ExecutionResponseImpl response = new ExecutionResponseImpl();
        List<ActionValue> actionValues = new ArrayList<>();
        response.setActionValues(actionValues);
        List<RuleInfo> ruleInfoResult = this.ruleBox.execute(filter, max, actionValues);
        List<RuleInfo> firedRules = new ArrayList<>(ruleInfoResult);
        KnowledgeSession session = (KnowledgeSession) this.context.getWorkingMemory();
        List<ReteInstance> reteInstanceList = session.getReteInstanceList();
        for (ReteInstance reteInstance : reteInstanceList) {
            reteInstance.reset();
        }

        response.setFiredRules(firedRules);
        response.addMatchedRules(this.matchedRules);
        return response;
    }

    public void addTrackers(Collection<FactTracker> list, boolean noCondition) {
        for (FactTracker tracker : list) {
            Activation activation = tracker.getActivation();

            Rule rule = activation.getRule();
            if (noCondition && rule.isWithElse()) {
                if (!this.ruleBox.getRules().contains(rule)) {
                    Rule elseRule = Utils.buildElseRule(rule);
                    ActivationImpl ac = new ActivationImpl(elseRule);
                    this.ruleBox.add(ac);
                }
            } else {
                this.ruleBox.add(activation);
            }
        }
    }

    public void retract(Object obj) {
        this.ruleBox.retract(obj);
    }

    public void clean() {
        this.ruleBox.clean();
    }
}