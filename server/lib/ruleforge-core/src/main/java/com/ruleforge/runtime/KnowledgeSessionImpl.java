package com.ruleforge.runtime;
import com.ruleforge.engine.EngineContext;
import com.ruleforge.engine.ValueCompute;
import com.ruleforge.engine.Path;
import com.ruleforge.engine.KnowledgeSession;
import com.ruleforge.engine.EvaluationContext;
import com.ruleforge.engine.Context;

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
import com.ruleforge.engine.RuleExecutionResponse;
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
    // V6.5 — 执行状态(context / evaluationContext / agenda / execMessageItems)已移到
    // ExecutionState (god class 拆分第四轮收口,延续 V6.2/V6.3/V6.4 模式)。
    private final ExecutionState executionState = new ExecutionState();
    // V6.4 — RETE 网络 + 子会话注册表已移到 ReteSessionRegistry (god class 拆分延续 V6.2/V6.3)。
    private final ReteSessionRegistry reteRegistry = new ReteSessionRegistry();
    // V6.3 — activation / agenda group 状态管理已移到 ActivationGroupRegistry (god class 拆分延续)。
    private final ActivationGroupRegistry activationRegistry = new ActivationGroupRegistry();
    private final SessionParameterManager paramManager = new SessionParameterManager();
    private final FactStore factStore = new FactStore();
    private KnowledgeEventManager knowledgeEventManager;

    public KnowledgeSessionImpl(KnowledgePackage knowledgePackage) {
        this((new KnowledgePackage[]{knowledgePackage}), null);
    }

    public KnowledgeSessionImpl(KnowledgePackage knowledgePackage, KnowledgeSession parentSession) {
        this(new KnowledgePackage[]{knowledgePackage}, parentSession);
    }

    public KnowledgeSessionImpl(KnowledgePackage[] knowledgePackages, KnowledgeSession parentSession) {
        // V6.5 — executionState / reteRegistry / activationRegistry / paramManager / factStore
        // 已在字段声明处初始化 (final)
        this.knowledgeEventManager = new KnowledgeEventManagerImpl();

        for (KnowledgePackage knowledgePackage : knowledgePackages) {
            reteRegistry.recordKnowledgePackage(knowledgePackage);
            this.paramManager.initFromKnowledgePackage(knowledgePackage);
        }

        this.initFromParentSession(parentSession);
        executionState.init(reteRegistry.getKnowledgePackageList(), this);
    }

    public void initFromParentSession(KnowledgeSession parentSession) {
        if (parentSession != null) {
            // V6.4 — parentSession + knowledgeSessionMap 委托 ReteSessionRegistry
            reteRegistry.setParentSession(parentSession);
            // 复用 parent 的 knowledgeSessionMap 同引用 (原 L83 字段重赋值语义,保留行为)
            reteRegistry.setKnowledgeSessionMap(parentSession.getKnowledgeSessionMap());
            this.knowledgeEventManager.getKnowledgeEventListeners().addAll(parentSession.getKnowledgeEventListeners());
            // V6.5 — execMessageItems 委托 ExecutionState (保留原 L82 字段重赋值语义)
            executionState.inheritExecMessageItemsFromParent(parentSession.getExecMessageItems());
            factStore.addAll(parentSession.getAllFactsList());
            this.paramManager.initFromParentSessionValueMap(parentSession.getSessionValueMap());
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
        this.paramManager.prepareForExecution(params, factStore.getFactMaps());
        factStore.add(this.paramManager.getParameterMap());

        long start = System.currentTimeMillis();
        evaluationRete(factStore.getAllFactsList());
        ExecutionResponseImpl resp = (ExecutionResponseImpl) executionState.getAgenda().execute(filter, max);
        resp.setDuration(System.currentTimeMillis() - start);
        reset();
        return resp;
    }

    // V6.2 — clearInitParameters / putDefaultParameter 已移到 SessionParameterManager

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
        return reteRegistry.getKnowledgePackageList();
    }

    public Object getParameter(String key) {
        return this.paramManager.getParameter(key);
    }

    public boolean update(Object obj) {
        this.reevaluate(obj);
        return true;
    }

    public boolean insert(Object fact) {
        if (!(fact instanceof GeneralEntity) && fact instanceof Map) {
            Map<?, ?> map = (Map) fact;
            factStore.getFactMaps().add(map);
            return false;
        } else {
            factStore.add(fact);
            return true;
        }
    }

    public boolean retract(Object fact) {
        executionState.getAgenda().retract(fact);
        // V5.82:按 reference equality 移除首次出现的 fact(同 className 多 fact 不再误删)
        factStore.remove(fact);
        return true;
    }

    public void assertFact(Object fact) {
        factStore.add(fact);
        this.reevaluate(fact);
    }

    public Map<String, Object> getParameters() {
        return this.paramManager.getParameters();
    }

    // V6.2 — addToFactsMap / getClassName 已移到 FactStore

    private void reset() {
        // V6.3 — activation / agenda group 状态委托 ActivationGroupRegistry.clear()
        activationRegistry.clear();
        executionState.getAgenda().clean();
        factStore.getFactMaps().clear();
        factStore.getAllFactsList().clear();
    }

    private void reevaluate(Object obj) {
        for (ReteInstance reteInstance : reteRegistry.getReteInstanceList()) {
            reteInstance.resetForReevaluate(obj);
        }

        List<Object> facts = new ArrayList<>();
        facts.add(obj);
        evaluationRete(facts);
    }

    private void evaluationRete(Collection<Object> facts) {
        // V5.100.8 — 3-level labeled loop (label84 outer / label82 middle / innermost
        // unit-find-valid) → 3 enhanced for + continue. 套 V6.3/V6.4 + V5.100.5/V5.100.7 模式。
        //   continue label84 (middle keys 耗尽) = 下一个 reteInstance = outer 下一次 iter
        //   continue label82 (innermost units 耗尽) = 下一个 key = middle 下一次 iter
        // 两处 cross-loop continue 在 enhanced for 里等价于 "自然走到下一层 iter"。 trackers
        // 变量保持 reteInstance scope (跟原 label84 body 内 Collection trackers 一致)。
        // 真 per-fact hot path — flatten 后必须跑 perf bench (HotPathBenchTest +
        // EvalBenchmarkV579) 确认无 wall-time 回归。 回归覆盖: 全量 + EffectiveDateWindow
        // 3 tests (activation-group + effective/expired 过滤, 锁 innermost find-valid)。
        for (ReteInstance reteInstance : reteRegistry.getReteInstanceList()) {
            Collection trackers = null;

            // 外层 per-reteInstance: 每个 fact 进入 rete 前清 EvaluationContext 缓存 + reset
            // sticky state。 V5.83 — 用 resetStickyStateOnly() 而非 reset(),保留 Path.passed
            // 累积状态,让 2-pattern join 能跨 fact 累积左右 fact 匹配 (见
            // [[v582-allfactsmap-rewrite]] TD-19.5.4)。
            for (Object fact : facts) {
                executionState.getEvaluationContext().clean();
                reteInstance.resetStickyStateOnly();
                this.doRete(reteInstance, fact, false);
            }

            this.doRete(reteInstance, "__*__", true);
            Map<String, List<ReteInstanceUnit>> agendaReteInstanceMap = reteInstance.getAgendaGroupReteInstancesMap();
            if (agendaReteInstanceMap != null) {
                activationRegistry.recordAgendaGroupUnits(agendaReteInstanceMap);
            }

            // 原 do-while-find (reteInstanceMap == null 则 skip 到下一个 reteInstance) →
            // null-check + continue. null activation group 的 reteInstance 仍跑上面 per-fact
            // processing, 只是 skip 下面 activation-group 处理。
            Map<String, List<ReteInstanceUnit>> reteInstanceMap = reteInstance.getActivationGroupReteInstancesMap();
            if (reteInstanceMap == null) {
                continue;
            }

            activationRegistry.recordActivationGroupUnits(reteInstanceMap);
            String reteInstanceId = reteInstance.getId();

            // 中层: 遍历 activation-group keys。 原 do-while-find (已 actived 的 skip) →
            // contains-check + continue。
            for (String key : reteInstanceMap.keySet()) {
                String id = reteInstanceId + key;
                if (activationRegistry.isActivated(id)) {
                    continue;
                }

                List<ReteInstanceUnit> insList = reteInstanceMap.get(key);

                // 内层: 遍历 group 内 units。 原 do-while-find-valid (skip not-effective +
                // skip expired) → 2 continue。 对首个能产生 trackers 的 unit, mark actived +
                // add + break 到下一个 key (trackers 非 empty 时 break); 全 units 不产生
                // trackers 则自然走到下一个 key (原 continue label82)。
                for (ReteInstanceUnit insUnit : insList) {
                    if (isNotYetEffective(insUnit)) {
                        continue;
                    }
                    if (isExpired(insUnit)) {
                        continue;
                    }

                    ReteInstance ri = insUnit.getReteInstance();
                    for (Object fact : facts) {
                        trackers = ri.enter(executionState.getEvaluationContext(), fact);
                        if (trackers != null && !trackers.isEmpty()) {
                            activationRegistry.markActivated(id);
                            executionState.getAgenda().addTrackers(trackers, false);
                            break;
                        }
                    }

                    if (trackers != null && !trackers.isEmpty()) {
                        break;
                    }
                }
            }
        }

        executionState.getEvaluationContext().clean();
    }

    private void doRete(ReteInstance reteInstance, Object fact, boolean noneCondition) {
        Collection<FactTracker> trackers = reteInstance.enter(executionState.getEvaluationContext(), fact);
        if (trackers != null) {
            executionState.getAgenda().addTrackers(trackers, noneCondition);
        }
    }

    public void activeRule(String activationGroupName, String ruleName) {
        // V5.100.6 — 砍 containsKey + get 双 lookup, 套 V5.93 原则. `map.get(key) == null`
        // 已能区分 absent vs null-value. 本场景 value 永为 List<ReteInstanceUnit> (非 null,
        // Rete.buildGroupRetesInstance 用 computeIfAbsent(k -> new ArrayList<>()) 装入, 无
        // put(key, null) 风险), 所以 get == null ↔ !containsKey 100% 等价. 节省 1 个
        // containsKey hash lookup per activeRule 调用 (低频: 用户显式激活 rule group).
        // V6.3 — 委托 ActivationGroupRegistry.getActivationGroupUnits().
        List<ReteInstanceUnit> unitList = activationRegistry.getActivationGroupUnits(activationGroupName);
        if (unitList == null) {
            throw new RuleException("Activation group [" + activationGroupName + "] not exist!");
        } else {
            // V5.100.7 — Iterator var4 + label42 (未用 label, Fernflower artifact) plain while
            // → enhanced for. 行为: 遍历 unitList, 只对 ruleName 匹配 + 有效期内的 unit 跑
            // rete enter (V5.82 走 allFactsList 全 fact).
            for (ReteInstanceUnit insUnit : unitList) {
                if (insUnit.getRuleName().equals(ruleName) && isWithinValidPeriod(insUnit)) {
                    ReteInstance reteIns = insUnit.getReteInstance();
                    // V5.82:走 allFactsList(全 fact),不再走 allFactsMap 的 last-wins 视图
                    for (Object fact : factStore.getAllFactsList()) {
                        Collection<FactTracker> trackers = reteIns.enter(executionState.getEvaluationContext(), fact);
                        if (trackers != null) {
                            executionState.getAgenda().addTrackers(trackers, false);
                        }
                    }
                }
            }

            executionState.getEvaluationContext().clean();
        }
    }

    public void activeAgendaGroup(String groupName) {
        // V5.100.6 — 砍 containsKey + get 双 lookup, 套 V5.93 原则 (跟 activeRule 同档).
        // value 永为 List<ReteInstanceUnit> (非 null, Rete.buildGroupRetesInstance 用
        // computeIfAbsent(k -> new ArrayList<>())).
        // V6.3 — 委托 ActivationGroupRegistry.getAgendaGroupUnits().
        List<ReteInstanceUnit> unitList = activationRegistry.getAgendaGroupUnits(groupName);
        if (unitList == null) {
            throw new RuleException("Agenda group [" + groupName + "] not exist!");
        } else {
            // V5.100.7 — while(true){do{do}while(isNotYetEffective)}while(isExpired)} find-valid
            // 状态机 → enhanced for + 2 continue. 套 V6.3/V6.4 模式 (do-while-find-non-null
            // → for + null-check-continue, 这里是 2 个 filter: skip not-yet-effective + skip
            // expired). 行为: 遍历 unitList, skip 未生效/已过期的 unit, 对有效 unit 跑 rete
            // enter (allFactsList + "__*__"). 原 while(true) 在 iterator 耗尽时 return,
            // for 自然耗尽后方法结束 (无 trailing clean, 跟原代码一致).
            for (ReteInstanceUnit insUnit : unitList) {
                if (isNotYetEffective(insUnit)) {
                    continue;
                }
                if (isExpired(insUnit)) {
                    continue;
                }

                ReteInstance reteIns = insUnit.getReteInstance();
                Collection<FactTracker> trackers = null;
                // V5.82:走 allFactsList(全 fact)
                for (Object fact : factStore.getAllFactsList()) {
                    trackers = reteIns.enter(executionState.getEvaluationContext(), fact);
                    if (trackers != null) {
                        executionState.getAgenda().addTrackers(trackers, false);
                    }
                }

                trackers = reteIns.enter(executionState.getEvaluationContext(), "__*__");
                if (trackers != null) {
                    executionState.getAgenda().addTrackers(trackers, true);
                }
            }
        }
    }

    public void writeLogFile() throws IOException {
        if (executionState.getExecMessageItems().size() != 0) {
            for (DebugWriter writer : EngineContext.getDebugWriters()) {
                writer.write(executionState.getExecMessageItems());
            }

            executionState.clearExecMessageItems();
        }
    }

    public List<MessageItem> getExecMessageItems() {
        return executionState.getExecMessageItems();
    }

    public Map<String, Object> getAllFactsMap() {
        return factStore.getAllFactsMap();
    }

    /**
     * V5.82 — 返 session 持有的全部 fact(同 className 多 fact 累加,不覆盖)。
     * 引擎 fireRules / activeRule / activeAgendaGroup 内部走本方法。
     */
    public List<Object> getAllFactsList() {
        return factStore.getAllFactsList();
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
        return reteRegistry.getKnowledgeSessionMap().get(id);
    }

    public void putKnowledgeSession(String id, KnowledgeSession session) {
        if (reteRegistry.getKnowledgeSessionMap().containsKey(id)) {
            reteRegistry.registerKnowledgeSession(id, session);
        }

    }

    public Object getSessionValue(String key) {
        return this.paramManager.getSessionValueMap().get(key);
    }

    public void setSessionValue(String key, Object value) {
        this.paramManager.getSessionValueMap().put(key, value);
    }

    public Map<String, Object> getSessionValueMap() {
        return this.paramManager.getSessionValueMap();
    }

    public Map<String, KnowledgeSession> getKnowledgeSessionMap() {
        return reteRegistry.getKnowledgeSessionMap();
    }

    public KnowledgeSession getParentSession() {
        return reteRegistry.getParentSession();
    }

    public Context getContext() {
        return executionState.getContext();
    }

    public List<ReteInstance> getReteInstanceList() {
        return reteRegistry.getReteInstanceList();
    }
}
