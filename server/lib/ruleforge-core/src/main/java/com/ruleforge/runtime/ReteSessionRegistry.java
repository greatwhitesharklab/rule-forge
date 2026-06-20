package com.ruleforge.runtime;

import com.ruleforge.engine.KnowledgeSession;
import com.ruleforge.runtime.rete.ReteInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V6.4 — 从 KnowledgeSessionImpl 抽取的 RETE 网络 + 子会话注册表
 * (god class 拆分延续 V6.2 SessionParameterManager / FactStore +
 * V6.3 ActivationGroupRegistry 模式)。
 *
 * <p>管四块状态:
 * <ul>
 *   <li>{@code knowledgePackageList} — 本会话加载的 KnowledgePackage 列表
 *       (构造器 init 时 {@code knowledgePackages} 数组装入)</li>
 *   <li>{@code reteInstanceList} — 每个 package 衍生的 ReteInstance 列表
 *       ({@code knowledgePackage.newReteInstance()} 1:1 生成)</li>
 *   <li>{@code knowledgeSessionMap} — 子会话注册表 (id → KnowledgeSession,
 *       由 {@link KnowledgeSessionImpl#putKnowledgeSession} 写入)</li>
 *   <li>{@code parentSession} — 父会话引用 (构造器 init 时设,
 *       用于 {@code initFromParentSession} 复制 parent 的 event listener /
 *       fact / sessionValueMap)</li>
 * </ul>
 *
 * <p>KnowledgeSession interface 上的 4 个 getter ({@code getKnowledgePackageList} /
 * {@code getReteInstanceList} / {@code getKnowledgeSessionMap} / {@code getParentSession})
 * 仍由 KnowledgeSessionImpl 提供, 内部委托本类。{@code Agenda.execute} 等外部代码通过
 * 这些 getter 访问, 不能改 interface 签名。
 */
public class ReteSessionRegistry {
    private final List<KnowledgePackage> knowledgePackageList = new ArrayList<>();
    private final List<ReteInstance> reteInstanceList = new ArrayList<>();
    private final Map<String, KnowledgeSession> knowledgeSessionMap = new HashMap<>();
    private KnowledgeSession parentSession;

    /**
     * 构造器 init 时调用 — 装入一个 KnowledgePackage + 其衍生 ReteInstance。
     */
    public void recordKnowledgePackage(KnowledgePackage knowledgePackage) {
        this.knowledgePackageList.add(knowledgePackage);
        this.reteInstanceList.add(knowledgePackage.newReteInstance());
    }

    /** 子会话注册表写入 (V6.4 兼容原 KnowledgeSessionImpl.putKnowledgeSession 语义)。 */
    public void registerKnowledgeSession(String id, KnowledgeSession session) {
        this.knowledgeSessionMap.put(id, session);
    }

    /** initFromParentSession: 设 parentSession。 */
    public void setParentSession(KnowledgeSession parentSession) {
        this.parentSession = parentSession;
    }

    /**
     * initFromParentSession: 复用 parent 的 knowledgeSessionMap 同引用
     * (V6.4 保留 KnowledgeSessionImpl 原 L83 字段重赋值语义, 子会话 put 到 map 会影响父会话)。
     */
    public void setKnowledgeSessionMap(Map<String, KnowledgeSession> map) {
        this.knowledgeSessionMap.clear();
        this.knowledgeSessionMap.putAll(map);
    }

    public List<KnowledgePackage> getKnowledgePackageList() {
        return this.knowledgePackageList;
    }

    public List<ReteInstance> getReteInstanceList() {
        return this.reteInstanceList;
    }

    public Map<String, KnowledgeSession> getKnowledgeSessionMap() {
        return this.knowledgeSessionMap;
    }

    public KnowledgeSession getParentSession() {
        return this.parentSession;
    }

    /** V6.4 — test 反射访问 knowledgePackageList (V6.2/V6.3 兼容字段迁移)。 */
    public List<KnowledgePackage> getKnowledgePackages() {
        return this.knowledgePackageList;
    }
}