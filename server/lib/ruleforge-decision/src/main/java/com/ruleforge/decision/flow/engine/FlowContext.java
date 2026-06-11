package com.ruleforge.decision.flow.engine;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.runtime.KnowledgeSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策流执行上下文。每条 evaluate 独立一个 FlowContext,不可跨请求共享。
 *
 * <p>字段语义:
 * <ul>
 *   <li>flowRunId: 单次执行 UUID,关联 nd_decision_flow_state / nd_decision_flow_log</li>
 *   <li>vars: 流程变量(兼容字段;V5.33 A0 起委托给 currentToken.vars)</li>
 *   <li>session: KnowledgeSession,RuleNodeExecutor 拿它跑 fireRules</li>
 *   <li>outputModel: 业务侧 POJO(Object,V5.18 修法保留 — RuleNodeExecutor 用 BeanUtils.describe/populate
 *                   反射处理任意类型,不直接引用 executor-app 的 OutputModel 类,守住模块边界)</li>
 *   <li>insertedEntities: 已 insert 进 session 的 entities,供状态恢复时序列化</li>
 *   <li>currentAwaitingField: 当前 userTask 等待写入的决策字段名,GatewayNodeExecutor 路由时读</li>
 *   <li>currentNodeId: 当前执行到的节点 id(委托给 currentToken)</li>
 *   <li><b>activeTokens</b> (V5.33 A0): 多 token 推进;fork 推 N 个 sub-token,join 合并</li>
 *   <li><b>currentToken</b> (V5.33 A0): 当前 executor 读 vars / nodeId 走 currentToken</li>
 *   <li><b>joinArrivals</b> (V5.33 A0): join_target_id → 已到达分支数,worklist 计数用</li>
 * </ul>
 *
 * <p>vars / currentNodeId 委托给 currentToken 是为了保持 API 兼容 — 6 处 caller
 * (DecisionServiceImpl / ShadowExecutionServiceImpl / FlowDecisionController /
 * TestController / TestServiceImpl / FlowStateRecoveryJob) 不需要改一行。
 */
public class FlowContext {
    private String flowRunId;
    private Map<String, Object> vars = new HashMap<>();
    private KnowledgeSession session;
    private Object outputModel;
    private List<GeneralEntity> insertedEntities;
    private String currentAwaitingField;
    private String currentNodeId;
    /**
     * V5.34 A2 — EndEvent 写出的 thrown error ref(Error/Escalation)。
     * 委托给 currentToken(跟 vars / currentNodeId 同套路),process-time 状态,不持久化。
     */
    private String thrownError;

    // V5.33 A0 — 多 token 模型
    /** 多 token 推进;worklist 由 traverse 主循环维护。 */
    private List<Token> activeTokens = new ArrayList<>();
    /** 当前 executor 看到的 token(fork/join 推进时切换)。 */
    private Token currentToken;
    /** join_target_id → 已到达分支数,持久化到 nd_decision_flow_state.join_arrivals JSON。 */
    private Map<String, Integer> joinArrivals = new HashMap<>();
    /**
     * V5.33 A0 — join_target_id → 已到达 join 的 token 列表。
     * join 齐了时,把列表里所有 token.vars union-merge 到 rootToken。
     */
    private Map<String, List<Token>> joinedTokens = new HashMap<>();

    public String getFlowRunId() { return flowRunId; }
    public void setFlowRunId(String flowRunId) { this.flowRunId = flowRunId; }

    /**
     * 兼容字段:返回 currentToken.vars(若 currentToken 为 null 返回本字段)。
     * 现有 caller(6 处)无需改;NodeExecutor 拿 vars 走这里即可。
     */
    public Map<String, Object> getVars() {
        if (currentToken != null) return currentToken.getVars();
        return vars;
    }

    public void setVars(Map<String, Object> vars) {
        if (currentToken != null) {
            currentToken.setVars(vars);
        } else {
            this.vars = vars == null ? new HashMap<>() : vars;
        }
    }

    public KnowledgeSession getSession() { return session; }
    public void setSession(KnowledgeSession session) { this.session = session; }

    public Object getOutputModel() { return outputModel; }
    public void setOutputModel(Object outputModel) { this.outputModel = outputModel; }

    public List<GeneralEntity> getInsertedEntities() { return insertedEntities; }
    public void setInsertedEntities(List<GeneralEntity> insertedEntities) { this.insertedEntities = insertedEntities; }

    public String getCurrentAwaitingField() { return currentAwaitingField; }
    public void setCurrentAwaitingField(String currentAwaitingField) { this.currentAwaitingField = currentAwaitingField; }

    /** 委托给 currentToken.currentNodeId(若 currentToken 为 null 返回本字段)。 */
    public String getCurrentNodeId() {
        if (currentToken != null) return currentToken.getCurrentNodeId();
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        if (currentToken != null) {
            currentToken.setCurrentNodeId(currentNodeId);
        } else {
            this.currentNodeId = currentNodeId;
        }
    }

    // -------- V5.34 A2 — thrownError(委托 currentToken) --------

    /** 委托给 currentToken.thrownError(若 currentToken 为 null 返回本字段)。 */
    public String getThrownError() {
        if (currentToken != null) return currentToken.getThrownError();
        return thrownError;
    }

    public void setThrownError(String thrownError) {
        if (currentToken != null) {
            currentToken.setThrownError(thrownError);
        } else {
            this.thrownError = thrownError;
        }
    }

    // V5.33 A0 — 多 token 字段

    public List<Token> getActiveTokens() { return activeTokens; }
    public void setActiveTokens(List<Token> activeTokens) {
        this.activeTokens = activeTokens == null ? new ArrayList<>() : activeTokens;
    }

    public Token getCurrentToken() { return currentToken; }
    public void setCurrentToken(Token currentToken) { this.currentToken = currentToken; }

    public Map<String, Integer> getJoinArrivals() { return joinArrivals; }
    public void setJoinArrivals(Map<String, Integer> joinArrivals) {
        this.joinArrivals = joinArrivals == null ? new HashMap<>() : joinArrivals;
    }

    public Map<String, List<Token>> getJoinedTokens() { return joinedTokens; }
    public void setJoinedTokens(Map<String, List<Token>> joinedTokens) {
        this.joinedTokens = joinedTokens == null ? new HashMap<>() : joinedTokens;
    }
}
