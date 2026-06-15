package com.ruleforge.runtime;

import com.ruleforge.debug.DebugWriter;
import com.ruleforge.debug.MessageItem;
import com.ruleforge.exception.RuleException;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.runtime.agenda.Agenda;
import com.ruleforge.runtime.agenda.AgendaFilter;
import com.ruleforge.runtime.event.KnowledgeEvent;
import com.ruleforge.runtime.event.KnowledgeEventListener;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.ruleforge.runtime.rete.*;

import java.io.IOException;
import java.util.*;

/**
 * 规则执行会话:持有知识包、RETE 实例、议程(fact → 激活 → 触发)、参数/fact/事件状态,
 * 是运行时执行的入口({@link #fireRules} 系列)。
 *
 * <p><b>注意</b>:{@link #evaluationRete}、{@link #activeRule}、{@link #activeAgendaGroup}
 * 含反编译遗留的标签化控制流({@code labelNN} / {@code varN}),逻辑微妙(激活组 + 生效/过期时间窗)。
 * 本类仅做安全的布尔抽取与构造器整理;<b>不要在无特征化测试覆盖的情况下重写其循环结构</b> ——
 * 深度清理需先补 characterization test 锁定现有行为。
 */
public class KnowledgeSessionImpl implements KnowledgeSession {
    private Context context;
    private EvaluationContextImpl evaluationContext;
    private Agenda agenda;
    private KnowledgeSession parentSession;
    private List<String> activedActivationGroup;
    private Map<String, Object> sessionValueMap;
    private List<MessageItem> execMessageItems;
    private Map<String, Object> initParameters;
    /**
     * V5.82 — 全部 fact 累加列表(同 className 多 fact 不覆盖,引擎 fireRules / activeRule
     * / activeAgendaGroup / retract 走本字段)。
     * <p>{@code allFactsMap} 仍是 {@code Map<String,Object>} last-wins 视图,通过
     * {@link #getAllFactsMap()} 惰性构建,保留旧 API 契约 — {@code ValueCompute.findObject} /
     * {@code LoopRule} / {@code KnowledgeSessionTest:265} 仍用。
     */
    private List<Object> allFactsList;
    private List<KnowledgePackage> knowledgePackageList;
    private List<ReteInstance> reteInstanceList;
    private Map<String, Object> parameterMap;
    private List<Map<?, ?>> factMaps;
    private Map<String, KnowledgeSession> knowledgeSessionMap;
    private Map<String, List<ReteInstanceUnit>> activationReteInstancesMap;
    private Map<String, List<ReteInstanceUnit>> agendaReteInstancesMap;
    private KnowledgeEventManager knowledgeEventManager;

    public KnowledgeSessionImpl(KnowledgePackage knowledgePackage) {
        this((new KnowledgePackage[]{knowledgePackage}), null);
    }

    public KnowledgeSessionImpl(KnowledgePackage knowledgePackage, KnowledgeSession parentSession) {
        this(new KnowledgePackage[]{knowledgePackage}, parentSession);
    }

    public KnowledgeSessionImpl(KnowledgePackage[] knowledgePackages, KnowledgeSession parentSession) {
        this.activedActivationGroup = new ArrayList<>();
        this.sessionValueMap = new HashMap<>();
        this.execMessageItems = new ArrayList<>();
        this.initParameters = new HashMap<>();
        this.allFactsList = new ArrayList<>();
        this.knowledgePackageList = new ArrayList<>();
        this.reteInstanceList = new ArrayList<>();
        this.parameterMap = new HashMap<>();
        this.factMaps = new ArrayList<>();
        this.knowledgeSessionMap = new HashMap<>();
        this.activationReteInstancesMap = new HashMap<>();
        this.agendaReteInstancesMap = new HashMap<>();
        this.knowledgeEventManager = new KnowledgeEventManagerImpl();

        for (KnowledgePackage knowledgePackage : knowledgePackages) {
            this.knowledgePackageList.add(knowledgePackage);
            this.reteInstanceList.add(knowledgePackage.newReteInstance());
            Map<String, String> params = knowledgePackage.getParameters();
            if (params != null) {
                for (String key : params.keySet()) {
                    putDefaultParameter(key, Datatype.valueOf(params.get(key)));
                }
            }
        }

        this.initFromParentSession(parentSession);
        this.initContext();
        this.agenda = new Agenda(this.context);
    }

    public void initFromParentSession(KnowledgeSession parentSession) {
        if (parentSession != null) {
            this.parentSession = parentSession;
            this.knowledgeEventManager.getKnowledgeEventListeners().addAll(parentSession.getKnowledgeEventListeners());
            this.execMessageItems = parentSession.getExecMessageItems();
            this.knowledgeSessionMap = parentSession.getKnowledgeSessionMap();
            this.allFactsList.addAll(parentSession.getAllFactsList());
            this.sessionValueMap.putAll(parentSession.getSessionValueMap());
        }
    }

    public RuleExecutionResponse fireRules() {
        return this.execute(null, null, 2147483647);
    }

    public RuleExecutionResponse fireRules(int max) {
        return this.execute(null, null, max);
    }

    public RuleExecutionResponse fireRules(AgendaFilter filter) {
        return this.execute(filter, null, 2147483647);
    }

    public RuleExecutionResponse fireRules(AgendaFilter filter, int max) {
        return this.execute(filter, null, max);
    }

    public RuleExecutionResponse fireRules(Map<String, Object> parameters) {
        return this.execute(null, parameters, 2147483647);
    }

    public RuleExecutionResponse fireRules(Map<String, Object> parameters, AgendaFilter filter) {
        return this.execute(filter, parameters, 2147483647);
    }

    public RuleExecutionResponse fireRules(Map<String, Object> parameters, AgendaFilter filter, int max) {
        return this.execute(filter, parameters, max);
    }

    public RuleExecutionResponse fireRules(Map<String, Object> parameters, int max) {
        return this.execute(null, parameters, max);
    }

    private RuleExecutionResponse execute(AgendaFilter filter, Map<String, Object> params, int max) {
        this.parameterMap.clear();
        clearInitParameters();
        this.parameterMap.putAll(this.initParameters);
        for (Map<?, ?> factMap : this.factMaps) {
            for (Object key : factMap.keySet()) {
                this.parameterMap.put(key.toString(), factMap.get(key));
            }
        }
        if (params != null) {
            this.parameterMap.putAll(params);
        }
        addToFactsMap(this.parameterMap);

        long start = System.currentTimeMillis();
        evaluationRete(this.allFactsList);
        ExecutionResponseImpl resp = (ExecutionResponseImpl) this.agenda.execute(filter, max);
        resp.setDuration(System.currentTimeMillis() - start);
        reset();
        return resp;
    }

    private void clearInitParameters() {
        List<String> stringList = new ArrayList<>();
        Iterator var2 = this.initParameters.keySet().iterator();

        String key;
        while (var2.hasNext()) {
            key = (String) var2.next();
            Object obj = this.initParameters.get(key);
            if (obj != null) {
                if (obj instanceof List) {
                    ((List) obj).clear();
                } else if (obj instanceof Set) {
                    ((Set) obj).clear();
                } else if (obj instanceof Map) {
                    ((Map) obj).clear();
                } else if (obj instanceof Number) {
                    this.initParameters.put(key, 0);
                } else if (obj instanceof Boolean) {
                    this.initParameters.put(key, false);
                } else if (obj instanceof String) {
                    stringList.add(key);
                }
            }
        }

        var2 = stringList.iterator();

        while (var2.hasNext()) {
            key = (String) var2.next();
            this.initParameters.remove(key);
        }

    }

    /** 按声明类型给参数放默认值(与原构造器 if-else 链等价;其他类型不放默认值)。 */
    private void putDefaultParameter(String key, Datatype type) {
        Object defaultValue;
        switch (type) {
            case Integer:
            case Long:
            case Double:
            case Float:
                defaultValue = 0;
                break;
            case Boolean:
                defaultValue = false;
                break;
            case List:
                defaultValue = new ArrayList<>();
                break;
            case Set:
                defaultValue = new HashSet<>();
                break;
            case Map:
                defaultValue = new HashMap<>();
                break;
            default:
                return;
        }
        this.initParameters.put(key, defaultValue);
    }

    /** 规则尚未生效(effectiveDate 在未来)。 */
    private static boolean isNotYetEffective(ReteInstanceUnit unit) {
        Date effectiveDate = unit.getEffectiveDate();
        return effectiveDate != null && effectiveDate.getTime() > System.currentTimeMillis();
    }

    /** 规则已过期(expiresDate 在过去)。 */
    private static boolean isExpired(ReteInstanceUnit unit) {
        Date expiresDate = unit.getExpiresDate();
        return expiresDate != null && expiresDate.getTime() < System.currentTimeMillis();
    }

    /** 规则在当前时刻处于有效期内(已生效且未过期)。 */
    private static boolean isWithinValidPeriod(ReteInstanceUnit unit) {
        return !isNotYetEffective(unit) && !isExpired(unit);
    }

    public List<KnowledgePackage> getKnowledgePackageList() {
        return this.knowledgePackageList;
    }

    public Object getParameter(String key) {
        return this.parameterMap.get(key);
    }

    public boolean update(Object obj) {
        this.reevaluate(obj);
        return true;
    }

    public boolean insert(Object fact) {
        if (!(fact instanceof GeneralEntity) && fact instanceof Map) {
            Map<?, ?> map = (Map) fact;
            this.factMaps.add(map);
            return false;
        } else {
            this.addToFactsMap(fact);
            return true;
        }
    }

    public boolean retract(Object fact) {
        this.agenda.retract(fact);
        // V5.82:按 reference equality 移除首次出现的 fact(同 className 多 fact 不再误删)
        this.allFactsList.remove(fact);
        return true;
    }

    public void assertFact(Object fact) {
        this.addToFactsMap(fact);
        this.reevaluate(fact);
    }

    public Map<String, Object> getParameters() {
        return this.parameterMap;
    }

    private void addToFactsMap(Object fact) {
        // V5.82:累加到 List<Object> allFactsList,不再按 className 覆盖
        this.allFactsList.add(fact);
    }

    private String getClassName(Object fact) {
        String className = null;
        if (fact instanceof GeneralEntity) {
            GeneralEntity ge = (GeneralEntity) fact;
            className = ge.getTargetClass();
        } else if (fact != null) {
            className = fact.getClass().getName();
        }

        return className;
    }

    private void reset() {
        this.activationReteInstancesMap.clear();
        this.agenda.clean();
        this.factMaps.clear();
        this.allFactsList.clear();
        this.activedActivationGroup.clear();
        this.agendaReteInstancesMap.clear();
    }

    private void reevaluate(Object obj) {
        for (ReteInstance reteInstance : this.reteInstanceList) {
            reteInstance.resetForReevaluate(obj);
        }

        List<Object> facts = new ArrayList<>();
        facts.add(obj);
        evaluationRete(facts);
    }

    private void evaluationRete(Collection<Object> facts) {
        Iterator<ReteInstance> reteInstanceIterator = this.reteInstanceList.iterator();

        label84:
        while (true) {
            ReteInstance reteInstance;
            Collection trackers = null;
            Map reteInstanceMap;
            do {
                if (!reteInstanceIterator.hasNext()) {
                    this.evaluationContext.clean();
                    return;
                }

                reteInstance = reteInstanceIterator.next();
                for (Object fact : facts) {
                    // V5.83 — 每个 fact 进入 rete 前清 EvaluationContext 缓存 + reset sticky state。
                    // 原代码只在 reteInstance 列表跑完才 clean/reset,导致:
                    // 1. criteria 结果跨 fact 复用(criteriaValueMap / partValueMap 缓存污染)
                    // 2. passAndNode 短路跨 fact 粘滞(CriteriaActivity.passed / AndActivity.passed)
                    // 这是修 pre-existing rete "sticky state" 缺陷的关键改动 — 见
                    // [[v582-allfactsmap-rewrite]] TD-19.5.4。
                    // 注意:用 resetStickyStateOnly() 而非 reset() — 保留 Path.passed 累积状态,
                    // 让 2-pattern join 能跨 fact 累积左右 fact 匹配。
                    this.evaluationContext.clean();
                    reteInstance.resetStickyStateOnly();
                    this.doRete(reteInstance, fact, false);
                }

                this.doRete(reteInstance, "__*__", true);
                Map<String, List<ReteInstanceUnit>> agendaReteInstanceMap = reteInstance.getAgendaGroupReteInstancesMap();
                if (agendaReteInstanceMap != null) {
                    this.agendaReteInstancesMap.putAll(agendaReteInstanceMap);
                }

                reteInstanceMap = reteInstance.getActivationGroupReteInstancesMap();
            } while (reteInstanceMap == null);

            this.activationReteInstancesMap.putAll(reteInstanceMap);
            Iterator var7 = reteInstanceMap.keySet().iterator();

            label82:
            while (true) {
                String key;
                String id;
                do {
                    if (!var7.hasNext()) {
                        continue label84;
                    }

                    key = (String) var7.next();
                    id = reteInstance.getId() + key;
                } while (this.activedActivationGroup.contains(id));

                List<ReteInstanceUnit> insList = (List<ReteInstanceUnit>) reteInstanceMap.get(key);
                Iterator<ReteInstanceUnit> var11 = insList.iterator();

                while (true) {
                    ReteInstanceUnit insUnit;
                    do {
                        do {
                            if (!var11.hasNext()) {
                                continue label82;
                            }

                            insUnit = var11.next();
                        } while (isNotYetEffective(insUnit));
                    } while (isExpired(insUnit));

                    ReteInstance ri = insUnit.getReteInstance();
                    for (Object fact : facts) {
                        trackers = ri.enter(this.evaluationContext, fact);
                        if (trackers != null && !trackers.isEmpty()) {
                            this.activedActivationGroup.add(id);
                            this.agenda.addTrackers(trackers, false);
                            break;
                        }
                    }

                    if (trackers != null && !trackers.isEmpty()) {
                        break;
                    }
                }
            }
        }
    }

    private void doRete(ReteInstance reteInstance, Object fact, boolean noneCondition) {
        Collection<FactTracker> trackers = reteInstance.enter(this.evaluationContext, fact);
        if (trackers != null) {
            this.agenda.addTrackers(trackers, noneCondition);
        }
    }

    public void activeRule(String activationGroupName, String ruleName) {
        if (!this.activationReteInstancesMap.containsKey(activationGroupName)) {
            throw new RuleException("Activation group [" + activationGroupName + "] not exist!");
        } else {
            List<ReteInstanceUnit> unitList = this.activationReteInstancesMap.get(activationGroupName);
            Iterator var4 = unitList.iterator();

            label42:
            while (var4.hasNext()) {
                ReteInstanceUnit insUnit = (ReteInstanceUnit) var4.next();
                if (insUnit.getRuleName().equals(ruleName) && isWithinValidPeriod(insUnit)) {
                    ReteInstance reteIns = insUnit.getReteInstance();
                    // V5.82:走 allFactsList(全 fact),不再走 allFactsMap 的 last-wins 视图
                    for (Object fact : this.allFactsList) {
                        Collection<FactTracker> trackers = reteIns.enter(this.evaluationContext, fact);
                        if (trackers != null) {
                            this.agenda.addTrackers(trackers, false);
                        }
                    }
                }
            }

            this.evaluationContext.clean();
        }
    }

    public void activeAgendaGroup(String groupName) {
        if (!this.agendaReteInstancesMap.containsKey(groupName)) {
            throw new RuleException("Agenda group [" + groupName + "] not exist!");
        } else {
            List<ReteInstanceUnit> unitList = this.agendaReteInstancesMap.get(groupName);
            Iterator var3 = unitList.iterator();

            while (true) {
                ReteInstanceUnit insUnit;
                do {
                    do {
                        if (!var3.hasNext()) {
                            return;
                        }

                        insUnit = (ReteInstanceUnit) var3.next();
                    } while (isNotYetEffective(insUnit));
                } while (isExpired(insUnit));

                ReteInstance reteIns = insUnit.getReteInstance();
                Collection<FactTracker> trackers = null;
                // V5.82:走 allFactsList(全 fact)
                for (Object fact : this.allFactsList) {
                    trackers = reteIns.enter(this.evaluationContext, fact);
                    if (trackers != null) {
                        this.agenda.addTrackers(trackers, false);
                    }
                }

                trackers = reteIns.enter(this.evaluationContext, "__*__");
                if (trackers != null) {
                    this.agenda.addTrackers(trackers, true);
                }
            }
        }
    }

    public void writeLogFile() throws IOException {
        if (this.execMessageItems.size() != 0) {
            for (DebugWriter writer : EngineContext.getDebugWriters()) {
                writer.write(this.execMessageItems);
            }

            this.execMessageItems.clear();
        }
    }

    public List<MessageItem> getExecMessageItems() {
        return this.execMessageItems;
    }

    public Map<String, Object> getAllFactsMap() {
        // V5.82:返 last-wins Map 视图(保留旧 API 契约 — ValueCompute.findObject / LoopRule /
        // KnowledgeSessionTest:265 都按"className 命中一个 fact"语义用)。
        Map<String, Object> view = new HashMap<>();
        for (Object fact : this.allFactsList) {
            view.put(this.getClassName(fact), fact);
        }
        return view;
    }

    /**
     * V5.82 — 返 session 持有的全部 fact(同 className 多 fact 累加,不覆盖)。
     * 引擎 fireRules / activeRule / activeAgendaGroup 内部走本方法。
     */
    public List<Object> getAllFactsList() {
        return this.allFactsList;
    }

    public void addEventListener(KnowledgeEventListener listener) {
        this.knowledgeEventManager.addEventListener(listener);
    }

    public List<KnowledgeEventListener> getKnowledgeEventListeners() {
        return this.knowledgeEventManager.getKnowledgeEventListeners();
    }

    public boolean removeEventListener(KnowledgeEventListener listener) {
        return this.knowledgeEventManager.removeEventListener(listener);
    }

    public void fireEvent(KnowledgeEvent event) {
        this.knowledgeEventManager.fireEvent(event);
    }

    public KnowledgeSession getKnowledgeSession(String id) {
        return this.knowledgeSessionMap.get(id);
    }

    public void putKnowledgeSession(String id, KnowledgeSession session) {
        if (this.knowledgeSessionMap.containsKey(id)) {
            this.knowledgeSessionMap.put(id, session);
        }

    }

    public Object getSessionValue(String key) {
        return this.sessionValueMap.get(key);
    }

    public void setSessionValue(String key, Object value) {
        this.sessionValueMap.put(key, value);
    }

    public Map<String, Object> getSessionValueMap() {
        return this.sessionValueMap;
    }

    public Map<String, KnowledgeSession> getKnowledgeSessionMap() {
        return this.knowledgeSessionMap;
    }

    public KnowledgeSession getParentSession() {
        return this.parentSession;
    }

    private void initContext() {
        Map<String, String> allVariableCategoryMap = null;

        for (KnowledgePackage knowledgePackage : this.knowledgePackageList) {
            if (allVariableCategoryMap == null) {
                allVariableCategoryMap = knowledgePackage.getVariableCateogoryMap();
            } else {
                allVariableCategoryMap.putAll(knowledgePackage.getVariableCateogoryMap());
            }
        }

        this.context = new ContextImpl(this, allVariableCategoryMap, this.execMessageItems);
        this.evaluationContext = new EvaluationContextImpl(this, allVariableCategoryMap, this.execMessageItems);
    }

    public Context getContext() {
        return this.context;
    }

    public List<ReteInstance> getReteInstanceList() {
        return this.reteInstanceList;
    }
}
