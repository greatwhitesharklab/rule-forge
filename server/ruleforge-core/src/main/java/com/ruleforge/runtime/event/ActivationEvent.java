package com.ruleforge.runtime.event;

import com.ruleforge.runtime.agenda.Activation;

/**
 * @author Jacky.gao
 * @since 2015年7月20日
 */
public interface ActivationEvent extends KnowledgeEvent{
	Activation getActivation();
}
