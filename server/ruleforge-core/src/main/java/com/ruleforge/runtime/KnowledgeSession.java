package com.ruleforge.runtime;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.runtime.agenda.AgendaFilter;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.runtime.rete.ReteInstance;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface KnowledgeSession extends WorkingMemory {
    RuleExecutionResponse fireRules();

    RuleExecutionResponse fireRules(AgendaFilter var1);

    RuleExecutionResponse fireRules(Map<String, Object> var1, AgendaFilter var2);

    RuleExecutionResponse fireRules(int var1);

    RuleExecutionResponse fireRules(Map<String, Object> var1, int var2);

    RuleExecutionResponse fireRules(AgendaFilter var1, int var2);

    RuleExecutionResponse fireRules(Map<String, Object> var1, AgendaFilter var2, int var3);

    RuleExecutionResponse fireRules(Map<String, Object> var1);

    void writeLogFile() throws IOException;

    List<MessageItem> getExecMessageItems();

    List<KnowledgePackage> getKnowledgePackageList();

    List<ReteInstance> getReteInstanceList();

    Map<String, KnowledgeSession> getKnowledgeSessionMap();

    KnowledgeSession getParentSession();

    void initFromParentSession(KnowledgeSession var1);
}
