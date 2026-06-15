package com.ruleforge.runtime.rete;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReteInstance {
    private Map<String, List<ReteInstanceUnit>> activationGroupReteInstancesMap;
    private Map<String, List<ReteInstanceUnit>> agendaGroupReteInstancesMap;
    private List<ObjectTypeActivity> objectTypeActivities;
    private String id = UUID.randomUUID().toString();

    public ReteInstance(List<ObjectTypeActivity> objectTypeActivities, Map<String, List<ReteInstanceUnit>> activationGroupReteInstancesMap, Map<String, List<ReteInstanceUnit>> agendaGroupReteInstancesMap) {
        this.objectTypeActivities = objectTypeActivities;
        this.activationGroupReteInstancesMap = activationGroupReteInstancesMap;
        this.agendaGroupReteInstancesMap = agendaGroupReteInstancesMap;
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
        for (ObjectTypeActivity objectTypeActivity : objectTypeActivities) {
            resetStickyActivities(objectTypeActivity.getPaths());
        }
    }

    private void resetStickyActivities(List<Path> paths) {
        if (paths == null) return;
        for (Path path : paths) {
            Activity activity = path.getTo();
            if (activity instanceof AbstractActivity) {
                AbstractActivity ac = (AbstractActivity) activity;
                ac.reset();
            }
            resetStickyActivities(activity.getPaths());
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

}
