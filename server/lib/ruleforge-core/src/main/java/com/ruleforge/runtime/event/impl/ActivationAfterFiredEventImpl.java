package com.ruleforge.runtime.event.impl;

import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.agenda.Activation;
import com.ruleforge.runtime.event.ActivationAfterFiredEvent;

/**
 * @author Jacky.gao
 * 2015年7月21日
 */
public class ActivationAfterFiredEventImpl extends DefaultActivationEvent implements ActivationAfterFiredEvent {
    public ActivationAfterFiredEventImpl(Activation activation, KnowledgeSession knowledgeSession) {
        super(activation, knowledgeSession);
    }
}
