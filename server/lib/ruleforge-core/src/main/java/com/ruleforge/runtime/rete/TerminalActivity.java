package com.ruleforge.runtime.rete;
import com.ruleforge.engine.EvaluationContext;

import com.ruleforge.debug.MsgType;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.engine.KnowledgeSession;
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

        // V6.9.11 — debug gate (V5.88 CriteriaActivity.logMessage 早返 / V5.90 Rule.debug 默认 /
        // V6.9.9-V6.9.10 action logMsg 门控 同档): skip 字符串拼接 + MessageItem 分配 +
        // ArrayList.add 当 rule.debug=false (V5.90 默认) 时。 Rule.debug 是 Boolean 可空,
        // 用 Boolean.TRUE.equals(...) 兼容 null。
        if (Boolean.TRUE.equals(this.rule.getDebug())) {
            String msg = "√√√ 规则【" + this.rule.getName() + "】成功匹配";
            context.logMsg(msg, MsgType.RuleMatch);
        }

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
