package com.ruleforge.runtime;

import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.rete.ReteInstance;

import java.util.List;
import java.util.Map;

public interface KnowledgePackage {
    Rete getRete();

    Map<String, String> getVariableCateogoryMap();

    Map<String, String> getParameters();

    ReteInstance newReteInstance();

    long getTimestamp();

    void resetTimestamp();

    List<Rule> getNoLhsRules();

    List<Rule> getWithElseRules();

    String getId();

    String getVersion();

    void setVersion(String version);
}
