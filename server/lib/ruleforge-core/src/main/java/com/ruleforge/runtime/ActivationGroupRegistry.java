package com.ruleforge.runtime;

import com.ruleforge.runtime.rete.ReteInstanceUnit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V6.3 — 从 KnowledgeSessionImpl 抽取的 activation / agenda group 状态管理
 * (god class 拆分延续 V6.2 的 SessionParameterManager / FactStore 模式)。
 *
 * <p>管三块状态:
 * <ul>
 *   <li>{@code activationReteInstancesMap} — activation group name → 关联 ReteInstanceUnit list
 *       (由 {@link com.ruleforge.runtime.KnowledgeSessionImpl#evaluationRete} 内
 *       {@code reteInstance.getActivationGroupReteInstancesMap()} putAll 装入)</li>
 *   <li>{@code agendaReteInstancesMap} — agenda group name → 关联 ReteInstanceUnit list
 *       (同上,源是 {@code reteInstance.getAgendaGroupReteInstancesMap()})</li>
 *   <li>{@code activedActivationGroup} — 已激活的 activation group id (reteInstanceId+key 拼接)
 *       set, 二次进入同一 group 跳过 (防 double-fire)。 <b>V6.7</b> 改 {@code HashSet} —
 *       {@code isActivated} 从 O(n) linear scan 降到 O(1) hash lookup, 累计 active group
 *       数大时 (10+) 收益显著。</li>
 * </ul>
 *
 * <p>KnowledgeSessionImpl 主类只编排 {@link KnowledgeSessionImpl#activeRule}/{@link
 * KnowledgeSessionImpl#activeAgendaGroup}/{@link KnowledgeSessionImpl#evaluationRete},
 * 不再直接持有上述 3 个字段。
 */
public class ActivationGroupRegistry {
    /** activation group name → 该 group 内 ReteInstanceUnit 列表。 */
    private final Map<String, List<ReteInstanceUnit>> activationReteInstancesMap = new HashMap<>();
    /** agenda group name → 该 group 内 ReteInstanceUnit 列表。 */
    private final Map<String, List<ReteInstanceUnit>> agendaReteInstancesMap = new HashMap<>();
    /** V6.7 — 已激活的 activation group id set (含 reteInstanceId 前缀, 防不同 reteInstance 串味)。 */
    private final Set<String> activedActivationGroup = new HashSet<>();

    /** reset() 时调用 — 清空全部 3 个状态。 */
    public void clear() {
        this.activationReteInstancesMap.clear();
        this.agendaReteInstancesMap.clear();
        this.activedActivationGroup.clear();
    }

    /** evaluationRete: 装入某个 reteInstance 的 activation group → units 映射。 */
    public void recordActivationGroupUnits(Map<String, List<ReteInstanceUnit>> reteInstanceMap) {
        if (reteInstanceMap == null) return;
        this.activationReteInstancesMap.putAll(reteInstanceMap);
    }

    /** evaluationRete: 装入某个 reteInstance 的 agenda group → units 映射。 */
    public void recordAgendaGroupUnits(Map<String, List<ReteInstanceUnit>> agendaReteInstanceMap) {
        if (agendaReteInstanceMap == null) return;
        this.agendaReteInstancesMap.putAll(agendaReteInstanceMap);
    }

    /**
     * evaluationRete: 检查 activation group id 是否已激活 (防 double-fire)。
     * V6.7 — HashSet O(1) contains (原 List O(n) linear scan)。
     */
    public boolean isActivated(String groupId) {
        return this.activedActivationGroup.contains(groupId);
    }

    /**
     * evaluationRete: 标记 activation group id 已激活 (trackers 非空时)。
     * V6.7 — HashSet.add 返 boolean (新 add = true, 重复 add = false), 行为同 List.add
     * (永远返 true) 在调用方未检查返值的语义下等价 (HashSet 自动 dedup)。
     */
    public void markActivated(String groupId) {
        this.activedActivationGroup.add(groupId);
    }

    /** activeRule: 拿 activation group name 关联的 unit list, 不存在返 null (V5.93 模式)。 */
    public List<ReteInstanceUnit> getActivationGroupUnits(String groupName) {
        return this.activationReteInstancesMap.get(groupName);
    }

    /** activeAgendaGroup: 拿 agenda group name 关联的 unit list, 不存在返 null (V5.93 模式)。 */
    public List<ReteInstanceUnit> getAgendaGroupUnits(String groupName) {
        return this.agendaReteInstancesMap.get(groupName);
    }

    /** V6.3 — test 用反射访问 activationReteInstancesMap (V6.2 兼容字段迁移)。 */
    public Map<String, List<ReteInstanceUnit>> getActivationReteInstancesMap() {
        return this.activationReteInstancesMap;
    }

    /** V6.3 — test 用反射访问 agendaReteInstancesMap (V6.2 兼容字段迁移)。 */
    public Map<String, List<ReteInstanceUnit>> getAgendaReteInstancesMap() {
        return this.agendaReteInstancesMap;
    }
}