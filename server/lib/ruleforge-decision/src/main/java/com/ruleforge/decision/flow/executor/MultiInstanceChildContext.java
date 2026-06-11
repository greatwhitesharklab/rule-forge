package com.ruleforge.decision.flow.executor;

import com.ruleforge.decision.flow.engine.FlowContext;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.runtime.KnowledgeSession;

import java.util.List;
import java.util.Map;

/**
 * V5.33 A1 — Multi-Instance parallel inner child context。
 *
 * <p>extends {@link FlowContext},重写 {@code getVars/setVars} 走 childVars map。
 * 其他 getter/setter 透传给 parent(共享 session / outputModel / currentToken / 等等)。
 *
 * <p>用途:parallel MI 跑 inner executor 时,inner 读 {@code ctx.getVars()} 拿到 child 隔离的 vars,
 * 写时也只写 childVars,parent 的 currentToken.vars 不被脏写(避免污染 A0 的 fork/join worklist)。
 *
 * <p>Sequential MI **不**用这个类 — 顺序在同一 ctx 上跑,vars 累积写。
 */
public class MultiInstanceChildContext extends FlowContext {

    private final FlowContext parent;
    private final Map<String, Object> childVars;

    public MultiInstanceChildContext(FlowContext parent, Map<String, Object> childVars) {
        this.parent = parent;
        this.childVars = childVars;
        // 把父 ctx 关键字段透传过来,保证 inner executor 走 session / outputModel 等
        setFlowRunId(parent.getFlowRunId());
        setSession(parent.getSession());
        setOutputModel(parent.getOutputModel());
        setInsertedEntities(parent.getInsertedEntities());
        setCurrentAwaitingField(parent.getCurrentAwaitingField());
        setCurrentToken(parent.getCurrentToken());
        setActiveTokens(parent.getActiveTokens());
        setJoinArrivals(parent.getJoinArrivals());
        setJoinedTokens(parent.getJoinedTokens());
    }

    @Override
    public Map<String, Object> getVars() {
        return childVars;
    }

    @Override
    public void setVars(Map<String, Object> vars) {
        // 写回 childVars(保留 inner executor 的 map 写语义)
        childVars.clear();
        if (vars != null) childVars.putAll(vars);
    }

    // 透传其他 getter(override 以避免走 super 默认行为带来的 currentToken 副作用)
    @Override
    public KnowledgeSession getSession() { return parent.getSession(); }
    @Override
    public Object getOutputModel() { return parent.getOutputModel(); }
    @Override
    public List<GeneralEntity> getInsertedEntities() { return parent.getInsertedEntities(); }
    @Override
    public String getCurrentAwaitingField() { return parent.getCurrentAwaitingField(); }
    @Override
    public String getCurrentNodeId() {
        // 透传:inner 不应该改 currentNodeId(由 FlowNodeRunner 推进)
        return parent.getCurrentNodeId();
    }
}
