package com.ruleforge.runtime;

import com.ruleforge.runtime.rete.Context;

import java.util.Map;


/**
 * @author Jacky.gao
 * @since 2015年1月8日
 */
public interface WorkingMemory extends EventManager {
    boolean insert(Object var1);

    void assertFact(Object var1);

    boolean update(Object var1);

    boolean retract(Object var1);

    Object getParameter(String var1);

    Map<String, Object> getParameters();

    Map<String, Object> getAllFactsMap();

    KnowledgeSession getKnowledgeSession(String var1);

    void putKnowledgeSession(String var1, KnowledgeSession var2);

    void setSessionValue(String var1, Object var2);

    Object getSessionValue(String var1);

    Map<String, Object> getSessionValueMap();

    void activeRule(String var1, String var2);

    void activeAgendaGroup(String var1);

    Context getContext();

}
