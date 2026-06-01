package com.ruleforge.model.rete.builder;

import com.ruleforge.Utils;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.Node;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.rete.*;
import com.ruleforge.model.rule.Other;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.lhs.BaseCriterion;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.loop.LoopRule;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.*;

/**
 * @author fred
 */
@NoArgsConstructor
public class ReteBuilder implements ApplicationContextAware {
    public static final String BEAN_ID = "ruleforge.reteBuilder";
    private static Collection<CriterionBuilder> criterionBuilders;

    public Rete buildRete(List<Rule> rules, ResourceLibrary resourceLibrary) {
        List<ObjectTypeNode> objectTypeNodes = new ArrayList<>();
        Rete rete = new Rete(objectTypeNodes, resourceLibrary);
        BuildContext context = new BuildContextImpl(resourceLibrary, objectTypeNodes);
        Map<String, List<Rule>> activationRulesMap = new HashMap<>();
        Map<String, List<Rule>> agendaRulesMap = new HashMap<>();

        for (Rule rule : rules) {
            if (!this.isPass(rule)) {
                Object agendaRules;
                if (StringUtils.isNotBlank(rule.getActivationGroup())) {
                    agendaRules = activationRulesMap.get(rule.getActivationGroup());
                    if (agendaRules == null) {
                        ArrayList<Rule> ruleArrayList = new ArrayList<>();
                        ruleArrayList.add(rule);
                        activationRulesMap.put(rule.getActivationGroup(), ruleArrayList);
                    } else {
                        ((List) agendaRules).add(rule);
                    }
                } else if (StringUtils.isNotBlank(rule.getAgendaGroup())) {
                    agendaRules = agendaRulesMap.get(rule.getAgendaGroup());
                    if (agendaRules == null) {
                        ArrayList<Rule> ruleArrayList = new ArrayList<>();
                        agendaRulesMap.put(rule.getAgendaGroup(), ruleArrayList);
                    } else {
                        ((List) agendaRules).add(rule);
                    }
                } else {
                    TerminalNode terminalNode = new TerminalNode(rule, context.nextId());
                    this.buildBranch(rule, context, terminalNode);
                    rule.setLhs(null);
                }
            }
        }

        rete.setActivationGroupRetesMap(this.buildRetesMap(activationRulesMap, context));
        rete.setAgendaGroupRetesMap(this.buildRetesMap(agendaRulesMap, context));
        return rete;
    }

    private boolean isPass(Rule rule) {
        if (rule.getEnabled() != null && !rule.getEnabled()) {
            return true;
        } else {
            Date expiresDate = rule.getExpiresDate();
            if (expiresDate != null) {
                Date now = new Date();
                return expiresDate.getTime() <= now.getTime();
            }

            return false;
        }
    }

    private Map<String, List<ReteUnit>> buildRetesMap(Map<String, List<Rule>> activationRulesMap, BuildContext parentContext) {
        if (activationRulesMap.isEmpty()) {
            return null;
        } else {
            ResourceLibrary resourceLibrary = (parentContext).getResourceLibrary();
            Map<String, List<ReteUnit>> reteMap = new HashMap<>();

            for (String groupName : activationRulesMap.keySet()) {
                List<Rule> rules = activationRulesMap.get(groupName);
                rules.sort((r1, r2) -> {
                    Integer o1 = r1.getSalience();
                    Integer o2 = r2.getSalience();
                    if (o1 != null && o2 != null) {
                        return o2 - o1;
                    } else if (o2 != null) {
                        return -1;
                    } else {
                        return o1 != null ? 1 : 0;
                    }
                });

                for (Rule rule : rules) {
                    List<ReteUnit> retes = reteMap.computeIfAbsent(groupName, k -> new ArrayList<>());

                    List<ObjectTypeNode> objectTypeNodes = new ArrayList<>();
                    BuildContext context = new BuildContextImpl(objectTypeNodes, parentContext);
                    TerminalNode terminalNode = new TerminalNode(rule, context.nextId());
                    Rete rete = new Rete(objectTypeNodes, resourceLibrary);
                    this.buildBranch(rule, context, terminalNode);
                    ReteUnit reteUnit = new ReteUnit(rete, rule.getName());
                    reteUnit.setEffectiveDate(rule.getEffectiveDate());
                    reteUnit.setExpiresDate(rule.getExpiresDate());
                    retes.add(reteUnit);
                    parentContext = context;
                    rule.setLhs(null);
                }
            }

            return reteMap;
        }
    }

    private void buildBranch(Rule rule, BuildContext context, TerminalNode terminalNode) {
        context.setCurrentRule(rule);
        Lhs lhs = rule.getLhs();
        if (!(rule instanceof LoopRule) && lhs != null && lhs.getCriterion() != null) {
            Criterion criterion = lhs.getCriterion();
            List<BaseReteNode> prevNodes = buildCriterion(context, criterion);

            BaseReteNode prevNode;
            for (Iterator<BaseReteNode> var7 = prevNodes.iterator(); var7.hasNext(); prevNode.addLine(terminalNode)) {
                prevNode = var7.next();
                if (prevNode instanceof JunctionNode) {
                    JunctionNode junctionNode = (JunctionNode) prevNode;
                    List<Line> toConnections = junctionNode.getToConnections();
                    if (toConnections.size() == 1) {
                        Line conn = toConnections.get(0);
                        Node fromNode = conn.getFrom();
                        if (fromNode instanceof CriteriaNode) {
                            CriteriaNode cnode = (CriteriaNode) fromNode;
                            cnode.getLines().remove(conn);
                            prevNode = cnode;
                        }
                    }
                }
            }

            Other other = rule.getOther();
            if (other != null && other.getActions() != null && !other.getActions().isEmpty()) {
                rule.setWithElse(true);
                Utils.buildElseRule(rule);
                ObjectTypeNode typeNode = context.buildObjectTypeNode("__*__");
                typeNode.addLine(terminalNode);
            }
        } else {
            ObjectTypeNode typeNode = context.buildObjectTypeNode("__*__");
            typeNode.addLine(terminalNode);
        }

    }

    public static List<BaseReteNode> buildCriterion(BuildContext context, Criterion criterion) {
        Iterator<CriterionBuilder> var2 = criterionBuilders.iterator();

        CriterionBuilder criterionBuilder;
        do {
            if (!var2.hasNext()) {
                throw new RuleException("Unknow criterion : " + criterion);
            }

            criterionBuilder = var2.next();
        } while (!criterionBuilder.support(criterion));

        return criterionBuilder.buildCriterion((BaseCriterion) criterion, context);
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        criterionBuilders = applicationContext.getBeansOfType(CriterionBuilder.class).values();
    }
}
