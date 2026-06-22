package com.ruleforge.decision.flow.engine;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.engine.KnowledgeSession;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.39 A1 — Rete 引擎耦合的状态容器。
 *
 * <p>RuleNodeExecutor 拿 {@link #getSession()} 跑 {@code fireRules},把 insert 的
 * {@link GeneralEntity} 累积在 {@link #getInsertedEntities()} 供状态恢复时序列化。
 *
 * <p>跟决策流业务状态(vars / token)完全解耦 — 单独容器避免 BusinessVars 混入
 * rete 引擎内部细节,也方便 V5.40+ 把 rete 整体替换时只动这一个类。
 */
public class ReteSession {

    private KnowledgeSession session;
    private final List<GeneralEntity> insertedEntities = new ArrayList<>();

    public KnowledgeSession getSession() {
        return session;
    }

    public void setSession(KnowledgeSession session) {
        this.session = session;
    }

    /**
     * 返回内部 insertedEntities list(live 引用,append-style)。
     * 序列化走 {@link #replaceSession(KnowledgeSession)} + getter 顺序读出。
     */
    public List<GeneralEntity> getInsertedEntities() {
        return insertedEntities;
    }

    /**
     * 一次性替换 session(re-evaluation 时使用)。insertedEntities 保留(已 insert 的
     * 事实不丢,re-evaluation 不能重做 insert)。
     *
     * @return {@code this} — 支持链式
     */
    public ReteSession replaceSession(KnowledgeSession newSession) {
        this.session = newSession;
        return this;
    }
}
