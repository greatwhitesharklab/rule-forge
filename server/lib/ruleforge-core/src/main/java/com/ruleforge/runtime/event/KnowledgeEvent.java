package com.ruleforge.runtime.event;

import com.ruleforge.engine.KnowledgeSession;

/**
 * @author Jacky.gao
 * @since 2015年7月20日
 */
public interface KnowledgeEvent {
	KnowledgeSession getKnowledgeSession();
}
