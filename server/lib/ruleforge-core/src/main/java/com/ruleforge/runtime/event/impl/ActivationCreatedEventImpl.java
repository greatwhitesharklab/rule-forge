package com.ruleforge.runtime.event.impl;

import com.ruleforge.engine.KnowledgeSession;
import com.ruleforge.runtime.agenda.Activation;
import com.ruleforge.runtime.event.ActivationCreatedEvent;

/**
 * @author Jacky.gao
 * @since 2015年7月21日
 */
public class ActivationCreatedEventImpl extends DefaultActivationEvent implements ActivationCreatedEvent{
	public ActivationCreatedEventImpl(Activation activation,
			KnowledgeSession knowledgeSession) {
		super(activation, knowledgeSession);
	}
}
