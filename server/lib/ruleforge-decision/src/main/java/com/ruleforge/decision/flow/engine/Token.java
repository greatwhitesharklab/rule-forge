package com.ruleforge.decision.flow.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * V5.33 A0 — 决策流执行 token。
 *
 * <p>一个 token 代表决策流图上的一条推进路径。V5.28 之前 Java 端单 token 模型(
 * 共享 ctx.vars);V5.33 A0 mirror Rust V5.28 P6,改成 per-token vars 隔离:
 * <ul>
 *   <li>fork 时从父 token 拍 vars 快照(深拷贝)</li>
 *   <li>join 时 union-merge 兄弟 token 的 vars(同 key 末班胜出)</li>
 *   <li>per-token visited Set 防环</li>
 * </ul>
 *
 * <p>vars 是 per-token 持有(非线程安全);KnowledgeSession 仍共享(per-flowRunId 唯一)。
 * V5.29 multi-instance 时再考虑 session 并行。
 */
public class Token {

    private final String tokenId;
    private String currentNodeId;
    private Map<String, Object> vars;
    private Set<String> visited;
    /** 此 token 所属 fork 的 join 节点 id;null 表示不在 fork 上。 */
    private String joinTarget;
    /**
     * V5.34 A2 — EndEvent 写出的 thrown error ref(Error/Escalation 用)。
     * process-time 状态,不持久化,fork/join 不传递(per-token 持有)。
     */
    private String thrownError;

    public Token(String tokenId) {
        this.tokenId = tokenId;
        this.currentNodeId = null;
        this.vars = new HashMap<>();
        this.visited = new HashSet<>();
        this.joinTarget = null;
    }

    /**
     * 从父 token 拍快照(深拷贝 vars + visited)。
     * 父子 token 修改各自 vars 互不影响。
     */
    public Token fork(String newTokenId) {
        Token child = new Token(newTokenId);
        child.vars = new HashMap<>(this.vars);
        child.visited = new HashSet<>(this.visited);
        child.joinTarget = this.joinTarget;
        return child;
    }

    /**
     * 把 other 的 vars + visited 合并进 this。vars 同 key 时 other 胜出(末班)。
     * 不会改 other 自身。
     *
     * <p>语义:join 时,末到的 token 覆盖先到的(后到者看到的是最新世界)。
     * Mirror Rust V5.28 P6 union-merge 契约。
     */
    public Token unionMerge(Token other) {
        if (other == null || other == this) return this;
        // 同 key 末班胜出:先 put 自己,再 put other(后写覆盖)
        this.vars.putAll(other.vars);
        this.visited.addAll(other.visited);
        return this;
    }

    /** 记录已访问节点;返回 true 表示新加入,false 表示已访问过(死循环)。 */
    public boolean visit(String nodeId) {
        return this.visited.add(nodeId);
    }

    public String getTokenId() { return tokenId; }
    public String getCurrentNodeId() { return currentNodeId; }
    public void setCurrentNodeId(String currentNodeId) { this.currentNodeId = currentNodeId; }
    public Map<String, Object> getVars() { return vars; }
    public void setVars(Map<String, Object> vars) {
        this.vars = vars == null ? new HashMap<>() : vars;
    }
    public Set<String> getVisited() { return visited; }
    /** 重置 visited(join 时拿父的 visited,允许再次走某些节点)。 */
    public void setVisited(Set<String> visited) {
        this.visited.clear();
        if (visited != null) this.visited.addAll(visited);
    }
    public String getJoinTarget() { return joinTarget; }
    public void setJoinTarget(String joinTarget) { this.joinTarget = joinTarget; }
    public String getThrownError() { return thrownError; }
    public void setThrownError(String thrownError) { this.thrownError = thrownError; }
}
