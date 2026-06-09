package com.ruleforge.runtime.event.impl;

import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.agenda.Activation;
import com.ruleforge.runtime.event.ActivationEvent;

/**
 * @author Jacky.gao
 * 2015年7月20日
 */
public class DefaultActivationEvent implements ActivationEvent {
    private Activation activation;
    private KnowledgeSession knowledgeSession;

    public DefaultActivationEvent(Activation activation, KnowledgeSession knowledgeSession) {
        this.activation = activation;
        this.knowledgeSession = knowledgeSession;
    }

    @Override
    public Activation getActivation() {
        return activation;
    }

    @Override
    public KnowledgeSession getKnowledgeSession() {
        return knowledgeSession;
    }
}
