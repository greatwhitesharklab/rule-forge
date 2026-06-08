package com.ruleforge.decision.flow.engine;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.runtime.KnowledgeSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策流执行上下文。每条 evaluate 独立一个 FlowContext,不可跨请求共享。
 *
 * 字段语义:
 * - flowRunId: 单次执行 UUID,关联 nd_decision_flow_state / nd_decision_flow_log
 * - vars: 流程变量,start 入口 = buildProcessVariables,NodeExecutor 互相写
 * - session: KnowledgeSession,RuleNodeExecutor 拿它跑 fireRules
 * - outputModel: 业务侧 POJO(Object,V5.18 修法保留 — RuleNodeExecutor 用 BeanUtils.describe/populate
 *               反射处理任意类型,不直接引用 executor-app 的 OutputModel 类,守住模块边界)
 * - insertedEntities: 已 insert 进 session 的 entities,供状态恢复时序列化
 * - currentAwaitingField: 当前 userTask 等待写入的决策字段名,GatewayNodeExecutor 路由时读
 * - currentNodeId: 当前执行到的节点 id,traverse 推进时更新
 */
public class FlowContext {
    private String flowRunId;
    private Map<String, Object> vars = new HashMap<>();
    private KnowledgeSession session;
    private Object outputModel;
    private List<GeneralEntity> insertedEntities;
    private String currentAwaitingField;
    private String currentNodeId;

    public String getFlowRunId() { return flowRunId; }
    public void setFlowRunId(String flowRunId) { this.flowRunId = flowRunId; }

    public Map<String, Object> getVars() { return vars; }
    public void setVars(Map<String, Object> vars) { this.vars = vars == null ? new HashMap<>() : vars; }

    public KnowledgeSession getSession() { return session; }
    public void setSession(KnowledgeSession session) { this.session = session; }

    public Object getOutputModel() { return outputModel; }
    public void setOutputModel(Object outputModel) { this.outputModel = outputModel; }

    public List<GeneralEntity> getInsertedEntities() { return insertedEntities; }
    public void setInsertedEntities(List<GeneralEntity> insertedEntities) { this.insertedEntities = insertedEntities; }

    public String getCurrentAwaitingField() { return currentAwaitingField; }
    public void setCurrentAwaitingField(String currentAwaitingField) { this.currentAwaitingField = currentAwaitingField; }

    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }
}
