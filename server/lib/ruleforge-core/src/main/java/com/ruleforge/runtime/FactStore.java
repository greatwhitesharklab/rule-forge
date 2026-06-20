package com.ruleforge.runtime;
import com.ruleforge.model.GeneralEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V6.2 — 从 KnowledgeSessionImpl 抽取的 fact 存储管理(god class 拆分)。
 * 管 allFactsList + factMaps。insert/retract/update/assertFact/addToFactsMap/getClassName。
 *
 * <p><b>V6.6</b> — {@link #getAllFactsMap()} 加 cache 失效。{@code ValueCompute.findObject}
 * 在 per-fact LHS 求值调 {@code getAllFactsMap()}, 旧实现每次新建 HashMap + 遍历
 * {@code allFactsList} (大 N fact 下浪费). cache 命中后从 O(N) build 降到 O(1) get,
 * per-fact 节省 HashMap 分配 + 遍历。
 */
public class FactStore {
    private final List<Object> allFactsList = new ArrayList<>();
    private final List<Map<?, ?>> factMaps = new ArrayList<>();

    /**
     * V6.6 — last-wins Map 视图 cache. {@code null} = dirty (下次 getAllFactsMap 重建)。
     * 写入触发失效的入口: {@link #add(Object)} / {@link #addAll(List)} /
     * {@link #remove(Object)} / {@link #clear()}。{@link #insert(Object)} 走 Map 型 fact
     * 分支不进 {@code allFactsList}, cache 不失效。
     */
    private Map<String, Object> allFactsMapCache;

    /** V6.6 — cache 失效。 */
    private void invalidateAllFactsMapCache() {
        this.allFactsMapCache = null;
    }

    /** V5.82:累加到 List<Object> allFactsList,不按 className 覆盖。 */
    public void add(Object fact) {
        allFactsList.add(fact);
        invalidateAllFactsMapCache();
    }

    public void addAll(List<Object> facts) {
        allFactsList.addAll(facts);
        invalidateAllFactsMapCache();
    }

    public boolean remove(Object fact) {
        boolean removed = allFactsList.remove(fact);
        if (removed) {
            invalidateAllFactsMapCache();
        }
        return removed;
    }

    /** Map 型 fact(非 GeneralEntity)单独存 factMaps,不进 allFactsList。 */
    public boolean insert(Object fact) {
        if (!(fact instanceof GeneralEntity) && fact instanceof Map) {
            factMaps.add((Map<?, ?>) fact);
            return false;
        }
        add(fact);
        return true;
    }

    public List<Object> getAllFactsList() {
        return allFactsList;
    }

    public List<Map<?, ?>> getFactMaps() {
        return factMaps;
    }

    /**
     * V6.6 — last-wins 视图 (向后兼容 ValueCompute.findObject / LoopRule) 加 cache 命中。
     * 外部 {@code LoopRule} / {@code ValueCompute.findObject} 只调 {@code .get(className)},
     * 不会 mutate cache, 所以返同一引用安全。
     */
    public Map<String, Object> getAllFactsMap() {
        Map<String, Object> map = this.allFactsMapCache;
        if (map == null) {
            map = new HashMap<>();
            for (Object fact : allFactsList) {
                String cls = getClassName(fact);
                if (cls != null) {
                    map.put(cls, fact);
                }
            }
            this.allFactsMapCache = map;
        }
        return map;
    }

    public static String getClassName(Object fact) {
        if (fact instanceof GeneralEntity) {
            return ((GeneralEntity) fact).getTargetClass();
        } else if (fact != null) {
            return fact.getClass().getName();
        }
        return null;
    }

    public void clear() {
        allFactsList.clear();
        factMaps.clear();
        invalidateAllFactsMapCache();
    }
}