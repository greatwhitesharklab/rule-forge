package com.ruleforge.runtime.event.impl;

import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.agenda.Activation;
import com.ruleforge.runtime.event.ActivationBeforeFiredEvent;

/**
 * @author Jacky.gao
 * 2015年7月21日
 */
public class ActivationBeforeFiredEventImpl extends DefaultActivationEvent implements ActivationBeforeFiredEvent {
    public ActivationBeforeFiredEventImpl(Activation activation, KnowledgeSession knowledgeSession) {
        super(activation, knowledgeSession);
    }
}
