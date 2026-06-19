package com.ruleforge.model.rule.loop;

import com.ruleforge.action.Action;
import com.ruleforge.action.ActionValue;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.KnowledgePackageWrapper;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.engine.Context;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Jacky.gao 2016年5月31日
 */
public class LoopRule extends Rule {

    private LoopStart loopStart;
    private LoopEnd loopEnd;
    private LoopTarget loopTarget;
    private List<LoopRuleUnit> units;
    private KnowledgePackageWrapper knowledgePackageWrapper;
    private Log log = LogFactory.getLog(this.getClass());

    public LoopRule() {
        this.setLoopRule(true);
    }

    public List<ActionValue> execute(Context context, Object matchedObject, List<Object> allMatchedObjects) {
        Object loopTargetObj = context.getValueCompute()
            .complexValueCompute(this.loopTarget.getValue(), matchedObject, context, allMatchedObjects);
        if (loopTargetObj == null) {
            this.log.warn("Loop rule [" + this.getName() + "] target value is null,cannot be executed.");
            return null;
        } else {
            List<ActionValue> values = new ArrayList<>();
            KnowledgeSession parentSession = (KnowledgeSession) context.getWorkingMemory();
            Map<String, Object> parameters = parentSession.getParameters();
            if (this.loopStart != null) {
                List<Action> startActions = this.loopStart.getActions();
                if (startActions != null) {
                    // V5.96 — Iterator var123 → enhanced for
                    for (Action action : startActions) {
                        if (this.getDebug() != null) {
                            action.setDebug(this.getDebug());
                        }

                        ActionValue value = action.execute(context, matchedObject, allMatchedObjects);
                        if (value != null) {
                            values.add(value);
                        }
                    }
                }
            }

            KnowledgeSession session = KnowledgeSessionFactory
                .newKnowledgeSession(this.knowledgePackageWrapper, context, parentSession);
            Map<String, Object> parentAllFactsMap = parentSession.getAllFactsMap();
            Object fact;
            if (loopTargetObj instanceof Collection) {
                Collection<?> collections = (Collection) loopTargetObj;
                String loopClazz = null;
                // V5.96 — Iterator var123 → enhanced for
                for (Object object : collections) {
                    if (loopClazz == null) {
                        if (object instanceof GeneralEntity) {
                            loopClazz = ((GeneralEntity) object).getTargetClass();
                        } else {
                            loopClazz = object.getClass().getName();
                        }
                    }

                    // V5.96 — Iterator var123 → enhanced for (keySet 是只读场景,for-each OK)
                    for (String className : parentAllFactsMap.keySet()) {
                        if (!className.equals(loopClazz)) {
                            fact = parentAllFactsMap.get(className);
                            session.insert(fact);
                        }
                    }

                    session.insert(object);
                    RuleExecutionResponse response = session.fireRules(parameters);
                    List<ActionValue> list = response.getActionValues();
                    boolean needBreak = false;
                    if (list != null) {
                        // V5.96 — Iterator var123 → enhanced for
                        for (ActionValue av : list) {
                            if (av.getActionId().equals("_loop_break__")) {
                                needBreak = true;
                            } else {
                                values.add(av);
                            }
                        }
                    }

                    parameters = new HashMap<>();
                    (parameters).putAll(session.getParameters());
                    if (needBreak) {
                        break;
                    }
                }
            } else {
                if (!(loopTargetObj instanceof Object[])) {
                    throw new RuntimeException("Loop rule target variable must be Collection or Object array type.");
                }

                Object[] objs = (Object[]) (loopTargetObj);
                // V5.96 — for (int var30=0;...) → enhanced for (Object[] 用 for-each 更直白)
                for (Object object : objs) {
                    // V5.96 — Iterator var123 → enhanced for
                    for (Object f : parentAllFactsMap.values()) {
                        fact = f;
                        session.insert(fact);
                    }

                    session.insert(object);
                    RuleExecutionResponse response = session.fireRules();
                    List<ActionValue> list = response.getActionValues();
                    boolean needBreak = false;
                    if (list != null) {
                        // V5.96 — Iterator var123 → enhanced for
                        for (ActionValue av : list) {
                            if (av.getActionId().equals("_loop_break__")) {
                                needBreak = true;
                            } else {
                                values.add(av);
                            }
                        }
                    }

                    parameters = new HashMap<>();
                    (parameters).putAll(session.getParameters());
                    if (needBreak) {
                        break;
                    }
                }
            }

            parentSession.getParameters().putAll(parameters);
            if (this.loopEnd != null) {
                List<Action> endActions = this.loopEnd.getActions();
                if (endActions != null) {
                    // V5.96 — Iterator var123 → enhanced for
                    for (Action action : endActions) {
                        if (this.getDebug() != null) {
                            action.setDebug(this.getDebug());
                        }

                        ActionValue value = action.execute(context, matchedObject, allMatchedObjects);
                        if (value != null) {
                            values.add(value);
                        }
                    }
                }
            }

            return values;
        }
    }

    public List<LoopRuleUnit> getUnits() {
        return this.units;
    }

    public void setUnits(List<LoopRuleUnit> units) {
        this.units = units;
    }

    public LoopStart getLoopStart() {
        return this.loopStart;
    }

    public void setLoopStart(LoopStart loopStart) {
        this.loopStart = loopStart;
    }

    public LoopEnd getLoopEnd() {
        return this.loopEnd;
    }

    public void setLoopEnd(LoopEnd loopEnd) {
        this.loopEnd = loopEnd;
    }

    public LoopTarget getLoopTarget() {
        return this.loopTarget;
    }

    public void setLoopTarget(LoopTarget loopTarget) {
        this.loopTarget = loopTarget;
    }

    public KnowledgePackageWrapper getKnowledgePackageWrapper() {
        return this.knowledgePackageWrapper;
    }

    public void setKnowledgePackageWrapper(KnowledgePackageWrapper knowledgePackageWrapper) {
        this.knowledgePackageWrapper = knowledgePackageWrapper;
    }
}
