package com.ruleforge.runtime.rete;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ReteInstance {
    private Map<String, List<ReteInstanceUnit>> activationGroupReteInstancesMap;
    private Map<String, List<ReteInstanceUnit>> agendaGroupReteInstancesMap;
    private List<ObjectTypeActivity> objectTypeActivities;
    /**
     * V5.92 — 预计算的 flat sticky activity 列表。{@link #resetStickyStateOnly()}
     * 走这个列表而非递归 walk,per-fact 节省 ~25ns(8 reset() + 4 递归 + 4 instanceof
     * 压成 4 reset() + 4 list iter)。构造时一次性 walk 整个 activity 子树生成,
     * immutable(LinkedHashSet 维护插入顺序 + dedup DAG)。
     */
    private final List<AbstractActivity> stickyActivities;
    private String id = UUID.randomUUID().toString();

    public ReteInstance(List<ObjectTypeActivity> objectTypeActivities, Map<String, List<ReteInstanceUnit>> activationGroupReteInstancesMap, Map<String, List<ReteInstanceUnit>> agendaGroupReteInstancesMap) {
        this.objectTypeActivities = objectTypeActivities;
        this.activationGroupReteInstancesMap = activationGroupReteInstancesMap;
        this.agendaGroupReteInstancesMap = agendaGroupReteInstancesMap;
        this.stickyActivities = computeStickyActivities(objectTypeActivities);
    }

    /**
     * V5.92 — 从所有 {@link ObjectTypeActivity} 路径递归 walk,收集 sticky
     * activity(CriteriaActivity / AndActivity / OrActivity)到 flat 列表。
     * 跳过 {@link TerminalActivity}(其 {@code reset()} 是 no-op,无意义调用)。
     * 用 {@link LinkedHashSet} dedup + 保留插入顺序,跟 V5.83 递归 walk 行为
     * 兼容(tree 场景完全一致,DAG 场景 reset() 调用次数减少但 reset() 幂等
     * 所以最终状态一致)。
     */
    private static List<AbstractActivity> computeStickyActivities(List<ObjectTypeActivity> otas) {
        Set<AbstractActivity> seen = new LinkedHashSet<>();
        if (otas == null) return List.of();
        for (ObjectTypeActivity ota : otas) {
            collectSticky(ota.getPaths(), seen);
        }
        return List.copyOf(seen);
    }

    private static void collectSticky(List<Path> paths, Set<AbstractActivity> out) {
        if (paths == null) return;
        for (Path path : paths) {
            Activity activity = path.getTo();
            if (activity instanceof AbstractActivity) {
                AbstractActivity ac = (AbstractActivity) activity;
                // 跳过 TerminalActivity:其 reset() 是 no-op,纯浪费 virtual dispatch
                if (!(activity instanceof TerminalActivity)) {
                    out.add(ac);
                }
                collectSticky(ac.getPaths(), out);
            }
        }
    }

    public Collection<FactTracker> enter(EvaluationContext context, Object obj) {
        Collection<FactTracker> trackers = null;
        for (ObjectTypeActivity objectTypeActivity : this.objectTypeActivities) {
            if (objectTypeActivity.support(obj)) {
                Collection<FactTracker> result = objectTypeActivity.enter(context, obj, new FactTracker());
                if (result != null) {
                    if (trackers == null) {
                        trackers = result;
                    } else {
                        trackers.addAll(result);
                    }
                }
            }
        }
        return trackers;
    }

    public List<ObjectTypeActivity> getObjectTypeActivities() {
        return objectTypeActivities;
    }

    public void reset() {
        for (ObjectTypeActivity objectTypeActivity : objectTypeActivities) {
            List<Path> paths = objectTypeActivity.getPaths();
            resetActivities(paths, false);
        }
    }

    /**
     * V5.83 — 重置活动节点的 sticky state(passed flag + currentTracker),但保留 Path.passed
     * 标记。这样新 fact 评估时,sticky 短路被清掉(避免 noise fact 污染后续匹配 fact),
     * 同时跨 fact 的 join 状态(Path.passed 累积)被保留 — 实现"per-fact fresh eval +
     * cross-fact join memory"。
     * <p>原 {@link #reset()} 还会清 Path.passed,会破坏 2-pattern join 的累积状态。
     */
    public void resetStickyStateOnly() {
        // V5.92 — 走预计算 flat 列表,无递归 + 无 instanceof + 无 Path.getTo() 虚拟调用。
        // per-fact 从 8 reset() + 4 递归 + 4 instanceof 压成 N reset() + N list iter(N
        // = 列表大小,2-class rete 是 4)。reset() 幂等,list 顺序跟 V5.83 递归 walk
        // 行为兼容(tree 场景一致,DAG 场景 reset() 调用次数减少但最终状态一致)。
        for (AbstractActivity ac : stickyActivities) {
            ac.reset();
        }
    }

    public void resetForReevaluate(Object valuateObj) {
        for (ObjectTypeActivity objectTypeActivity : objectTypeActivities) {
            if (objectTypeActivity.support(valuateObj)) {
                List<Path> paths = objectTypeActivity.getPaths();
                resetActivities(paths, true);
            }
        }
    }

    private void resetActivities(List<Path> paths, boolean forReevaluate) {
        if (paths == null) return;
        for (Path path : paths) {
            path.setPassed(false);
            Activity activity = path.getTo();
            if (forReevaluate) {
                if (activity instanceof OrActivity) {
                    OrActivity ac = (OrActivity) activity;
                    ac.reset();
                }
                if (activity instanceof CriteriaActivity) {
                    CriteriaActivity ac = (CriteriaActivity) activity;
                    ac.reset();
                }
            } else {
                if (activity instanceof AbstractActivity) {
                    AbstractActivity ac = (AbstractActivity) activity;
                    ac.reset();
                }
            }
            resetActivities(activity.getPaths(), forReevaluate);
        }
    }

    public Map<String, List<ReteInstanceUnit>> getActivationGroupReteInstancesMap() {
        return this.activationGroupReteInstancesMap;
    }

    public Map<String, List<ReteInstanceUnit>> getAgendaGroupReteInstancesMap() {
        return this.agendaGroupReteInstancesMap;
    }

    public String getId() {
        return this.id;
    }

    /**
     * V5.92 — 返回预计算的 flat sticky activity 列表(测试用)。
     * package-private 仅给 {@code com.ruleforge.runtime.rete.*} 包内 test
     * (如 {@code ReteInstanceStickyListTest}) 访问,production 不应依赖。
     */
    List<AbstractActivity> getStickyActivitiesForTest() {
        return stickyActivities;
    }

}
