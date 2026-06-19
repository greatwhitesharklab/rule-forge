package com.ruleforge.runtime;
import com.ruleforge.engine.KnowledgeSession;

/**
 * @author Jacky.gao
 * @since 2015年9月29日
 */
public interface Business {
    void execute(KnowledgeSession session);
}
