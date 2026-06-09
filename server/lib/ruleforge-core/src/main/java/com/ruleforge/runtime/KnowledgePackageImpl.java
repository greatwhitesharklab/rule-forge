package com.ruleforge.runtime;

import com.ruleforge.model.Node;
import com.ruleforge.model.RuleJsonDeserializer;
import com.ruleforge.model.rete.*;
import com.ruleforge.model.rule.Other;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.rete.ReteInstance;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.*;

public class KnowledgePackageImpl implements KnowledgePackage {
    @Setter
    @Getter
    private Rete rete;
    @Setter
    private Map<String, String> variableCategoryMap = new HashMap<>();
    @Setter
    private Map<String, String> parameters;
    @JsonDeserialize(using = RuleJsonDeserializer.class)
    private List<Rule> noLhsRules;
    @JsonIgnore
    private Map<Rule, Rule> elseRulesMap = new HashMap<>();
    @JsonIgnore
    private List<Rule> withElseRules = new ArrayList<>();
    @Getter
    private long timestamp;
    @Getter
    private final String id = UUID.randomUUID().toString();
    private String version;

    public KnowledgePackageImpl() {
        timestamp = System.currentTimeMillis();
    }

    public void buildWithElseRules() {
        List<ObjectTypeNode> typeNodes = rete.getObjectTypeNodes();
        if (typeNodes != null) {
            for (ObjectTypeNode typeNode : typeNodes) {
                buildReteLinesForElseRules(typeNode.getLines());
            }
        }
    }

    private void buildReteLinesForElseRules(List<Line> lines) {
        if (lines == null) return;
        for (Line line : lines) {
            Node toNode = line.getTo();
            if (toNode == null) continue;
            if (toNode instanceof TerminalNode) {
                TerminalNode terminalNode = (TerminalNode) toNode;
                Rule rule = terminalNode.getRule();
                if (!withElseRules.contains(rule)) {
                    Other other = rule.getOther();
                    if (other != null && other.getActions() != null && other.getActions().size() > 0) {
                        withElseRules.add(rule);

                        Rule elseRule = new Rule();
                        elseRule.setName(rule.getName() + "else");
                        elseRule.setActivationGroup(rule.getActivationGroup());
                        elseRule.setAgendaGroup(rule.getAgendaGroup());
                        elseRule.setAutoFocus(rule.getAutoFocus());
                        elseRule.setEffectiveDate(rule.getEffectiveDate());
                        elseRule.setExpiresDate(rule.getExpiresDate());
                        elseRule.setEnabled(rule.getEnabled());
                        elseRule.setRuleflowGroup(rule.getRuleflowGroup());
                        elseRule.setSalience(rule.getSalience());
                        Rhs rhs = new Rhs();
                        rhs.setActions(other.getActions());
                        elseRule.setRhs(rhs);

                        elseRulesMap.put(rule, elseRule);
                    }
                }
            } else if (toNode instanceof BaseReteNode) {
                BaseReteNode reteNode = (BaseReteNode) toNode;
                buildReteLinesForElseRules(reteNode.getLines());
            }
        }
    }

    public Rule getElseRule(Rule rule) {
        return elseRulesMap.get(rule);
    }

    @Override
    public String getVersion() {
        return this.version;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }

    public void resetTimestamp() {
        timestamp = System.currentTimeMillis();
    }

    public Map<String, String> getVariableCateogoryMap() {
        return variableCategoryMap;
    }

    public void setNoLhsRules(List<Rule> noLhsRules) {
        this.noLhsRules = noLhsRules;
    }

    @Override
    public List<Rule> getNoLhsRules() {
        return noLhsRules;
    }

    @Override
    public List<Rule> getWithElseRules() {
        return this.withElseRules;
    }


    public ReteInstance newReteInstance() {
        return rete.newReteInstance();
    }

    @Override
    public Map<String, String> getParameters() {
        return this.parameters;
    }
}
