package com.ruleforge.runtime.rete;

import com.ruleforge.debug.MsgType;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.agenda.ActivationImpl;
import com.ruleforge.runtime.event.impl.ActivationCreatedEventImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TerminalActivity extends AbstractActivity {
    private Rule rule;

    public TerminalActivity(Rule rule) {
        this.rule = rule;
    }

    public Collection<FactTracker> enter(EvaluationContext context, Object obj, FactTracker tracker) {
        List<FactTracker> result = new ArrayList<>();
        ActivationImpl ac = new ActivationImpl(this.rule);
        tracker.setActivation(ac);
        result.add(tracker);
        KnowledgeSession session = (KnowledgeSession) context.getWorkingMemory();
        session.fireEvent(new ActivationCreatedEventImpl(ac, session));

        // 执行信息
        String msg = "√√√ 规则【" + this.rule.getName() + "】成功匹配";
        context.logMsg(msg, MsgType.RuleMatch);

        return result;
    }

    public void passAndNode() {
    }

    public boolean joinNodeIsPassed() {
        return false;
    }

    public void reset() {
    }
}
