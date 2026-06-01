package com.ruleforge.runtime.event.impl;

import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.agenda.Activation;
import com.ruleforge.runtime.event.ActivationCancelledEvent;

/**
 * @author Jacky.gao
 * @since 2015年7月21日
 */
public class ActivationCancelledEventImpl extends DefaultActivationEvent implements ActivationCancelledEvent{
	public ActivationCancelledEventImpl(Activation activation,
			KnowledgeSession knowledgeSession) {
		super(activation, knowledgeSession);
	}
}
