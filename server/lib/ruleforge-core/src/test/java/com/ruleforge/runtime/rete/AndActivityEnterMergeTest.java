package com.ruleforge.runtime.rete;
import com.ruleforge.engine.Path;
import com.ruleforge.engine.EvaluationContext;

import com.ruleforge.model.rule.lhs.BaseCriteria;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.rete.test.EngineContextWirer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.1 — {@link AndActivity#enter(EvaluationContext, Object, FactTracker)} 契约 BDD。
 *
 * <p>锁 V6.1 修法(用 {@code get + null check} 替代 {@code containsKey + put}
 * 双 lookup)的行为不变性:
 * <ul>
 *   <li>merge 阶段:新 tracker 的 map 中已存在的 key 保留新 tracker 的 value
 *       (currentMap 的同 key 不覆盖)</li>
 *   <li>merge 阶段:新 tracker 的 map 中缺失的 key,从 currentMap 拷过来</li>
 *   <li>merge 阶段:currentMap 为空 → 新 tracker 的 map 完全不变</li>
 *   <li>每次 {@code enter} 后 {@code this.currentTracker} 被替换为新 tracker</li>
 * </ul>
 *
 * <p><b>Why V6.1 选这条</b>:沿 V5.93/V5.94/V5.97/V5.98 立的"砍 HashMap.containsKey
 * + put/get 双 lookup"原则, {@code AndActivity.enter} 第 24-29 行是同样的反模式:
 * <pre>
 * for (Object key : currentMap.keySet()) {
 *     if (!map.containsKey(key)) {          // lookup 1
 *         map.put(key, currentMap.get(key)); // lookup 2 (only if absent)
 *     }
 * }
 * </pre>
 * 1:1 套 V5.93 修法:
 * <pre>
 * for (Object key : currentMap.keySet()) {
 *     if (map.get(key) == null) {           // lookup 1
 *         map.put(key, currentMap.get(key)); // lookup 2 (only if absent)
 *     }
 * }
 * </pre>
 * {@code HashMap.get} 对 missing key 返 null,与 {@code containsKey==false} 行为等价
 * (本场景 put 后 value 永远非 null,null-stored 路径不可达 — 跟 V5.97 同档)。
 *
 * <p>本方法在 V5.85 PerfScalingAnalysis JFR 抓出 666 sample(2-class rete 多次 join path),
 * per-fact 多次 iter,Bench 期望 -2-5us/scenario。
 *
 * <p><b>行为等价性 audit</b>: {@code get(key) == null} 在 "key 缺失" 和 "key 存在但 value 为
 * null" 都返 null;但 merge 的 value 是 {@code List<BaseCriteria>},正常 addObjectCriteria
 * 路径下 list 永远非 null(详见 V5.97 doc),所以 null-stored 路径不可达。
 *
 * @see com.ruleforge.docs.notes.v611-andactivity-double-lookup V6.1 完整 doc
 * @since 6.1
 */
@DisplayName("V6.1 — AndActivity.enter merge 契约 (修双 lookup)")
class AndActivityEnterMergeTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    /**
     * 给 AndActivity 装 1 个 fromPath 但保持 not passed,这样 isAllPassed() 返 false,
     * enter 直接返 null(避开 visitPaths 副作用)。这跟 V5.96 / V5.97 测试一致 —
     * 只关注 merge 阶段,不关注 join 后续行为。
     */
    private static AndActivity andWithUnpassedPath() {
        AndActivity and = new AndActivity();
        Path p = new Path(new ObjectTypeActivity("X"));
        // p.setPassed(false) is default; fromPaths.add(p)
        and.addFromPath(p);
        return and;
    }

    @Nested
    @DisplayName("merge 阶段:currentMap 同 key 不覆盖新 tracker")
    class MergePreservesNewTrackerValues {

        // Given: tracker_t1 addObjectCriteria("k1", c1) — currentMap {k1→[c1]}
        // And:   tracker_t2 addObjectCriteria("k1", c2) — t2.map {k1→[c2]}
        // When:  and.enter(null, null, t1); and.enter(null, null, t2);
        // Then:  t2.map[k1] 仍为 [c2] (不被 t1 的 [c1] 覆盖)
        @Test
        @DisplayName("新 tracker 中已存在的 key → merge 保留新 tracker 的 value")
        void mergePreservesExistingKeysInNewTracker() {
            BaseCriteria c1 = new Criteria();
            BaseCriteria c2 = new Criteria();

            FactTracker t1 = new FactTracker();
            t1.addObjectCriteria("k1", c1);
            FactTracker t2 = new FactTracker();
            t2.addObjectCriteria("k1", c2);

            AndActivity and = andWithUnpassedPath();
            and.enter(null, null, t1);
            and.enter(null, null, t2);

            Map<Object, List<BaseCriteria>> map = t2.getObjectCriteriaMap();
            assertThat(map).hasSize(1);
            assertThat(map.get("k1")).containsExactly(c2);
        }
    }

    @Nested
    @DisplayName("merge 阶段:currentMap 独有的 key 拷进新 tracker")
    class MergeCopiesMissingKeys {

        // Given: tracker_t1 addObjectCriteria("k1", c1) — currentMap {k1→[c1]}
        // And:   tracker_t2 addObjectCriteria("k2", c2) — t2.map {k2→[c2]}
        // When:  and.enter(null, null, t1); and.enter(null, null, t2);
        // Then:  t2.map 含 {k1→[c1], k2→[c2]} (k1 从 t1 拷过来)
        @Test
        @DisplayName("新 tracker 中缺失的 key → 从 currentMap 拷贝")
        void mergeCopiesKeysOnlyInCurrent() {
            BaseCriteria c1 = new Criteria();
            BaseCriteria c2 = new Criteria();

            FactTracker t1 = new FactTracker();
            t1.addObjectCriteria("k1", c1);
            FactTracker t2 = new FactTracker();
            t2.addObjectCriteria("k2", c2);

            AndActivity and = andWithUnpassedPath();
            and.enter(null, null, t1);
            and.enter(null, null, t2);

            Map<Object, List<BaseCriteria>> map = t2.getObjectCriteriaMap();
            assertThat(map).hasSize(2);
            assertThat(map.get("k1")).containsExactly(c1);
            assertThat(map.get("k2")).containsExactly(c2);
        }
    }

    @Nested
    @DisplayName("merge 阶段:混合(2 重叠 + 1 独有)")
    class MergeMixedOverlapAndUnique {

        // Given: t1 addObjectCriteria("k1", c1) + ("k2", c2) — currentMap {k1→[c1], k2→[c2]}
        // And:   t2 addObjectCriteria("k1", c1') + ("k3", c3) — t2.map {k1→[c1'], k3→[c3]}
        // When:  and.enter(null, null, t1); and.enter(null, null, t2);
        // Then:  t2.map = {k1→[c1'], k2→[c2] (从 t1 拷), k3→[c3]}
        @Test
        @DisplayName("overlap + unique 混合:overlap 保留,unique 拷贝")
        void mergeHandlesOverlapAndUniqueMixed() {
            BaseCriteria c1 = new Criteria();
            BaseCriteria c2 = new Criteria();
            BaseCriteria c1New = new Criteria();
            BaseCriteria c3 = new Criteria();

            FactTracker t1 = new FactTracker();
            t1.addObjectCriteria("k1", c1);
            t1.addObjectCriteria("k2", c2);
            FactTracker t2 = new FactTracker();
            t2.addObjectCriteria("k1", c1New);
            t2.addObjectCriteria("k3", c3);

            AndActivity and = andWithUnpassedPath();
            and.enter(null, null, t1);
            and.enter(null, null, t2);

            Map<Object, List<BaseCriteria>> map = t2.getObjectCriteriaMap();
            assertThat(map).hasSize(3);
            assertThat(map.get("k1")).containsExactly(c1New); // preserved
            assertThat(map.get("k2")).containsExactly(c2);    // copied from t1
            assertThat(map.get("k3")).containsExactly(c3);    // was already in t2
        }
    }

    @Nested
    @DisplayName("merge 阶段:currentMap 为空 → 新 tracker 的 map 完全不变")
    class MergeWithEmptyCurrent {

        // Given: t1 空 map (currentMap = {}), t2 addObjectCriteria("k1", c1)
        // When:  and.enter(null, null, t1); and.enter(null, null, t2);
        // Then:  t2.map 仍 {k1→[c1]} (空 merge 不抹除新 tracker 的内容)
        @Test
        @DisplayName("currentMap 为空 → 新 tracker 的 map 不变")
        void mergeWithEmptyCurrentLeavesNewTrackerUntouched() {
            BaseCriteria c1 = new Criteria();
            FactTracker t1 = new FactTracker(); // empty
            FactTracker t2 = new FactTracker();
            t2.addObjectCriteria("k1", c1);

            AndActivity and = andWithUnpassedPath();
            and.enter(null, null, t1);
            and.enter(null, null, t2);

            Map<Object, List<BaseCriteria>> map = t2.getObjectCriteriaMap();
            assertThat(map).hasSize(1);
            assertThat(map.get("k1")).containsExactly(c1);
        }
    }

    @Nested
    @DisplayName("merge 阶段:currentTracker 在每次 enter 后被替换")
    class CurrentTrackerReplacedOnEachEnter {

        // Given: AndActivity; t1, t2 各自 addObjectCriteria
        // When:  enter(t1) then enter(t2)
        // Then:  this.currentTracker 是 t2 (新 tracker 替换 currentTracker)
        //  — 用反射读 private field currentTracker
        @Test
        @DisplayName("每次 enter 后 this.currentTracker 指向新 tracker (last-wins)")
        void currentTrackerReplacedToLatest() throws Exception {
            FactTracker t1 = new FactTracker();
            t1.addObjectCriteria("k1", new Criteria());
            FactTracker t2 = new FactTracker();
            t2.addObjectCriteria("k2", new Criteria());

            AndActivity and = andWithUnpassedPath();
            and.enter(null, null, t1);
            and.enter(null, null, t2);

            java.lang.reflect.Field f = AndActivity.class.getDeclaredField("currentTracker");
            f.setAccessible(true);
            assertThat(f.get(and)).isSameAs(t2);
        }
    }

    @Nested
    @DisplayName("merge 阶段:first call 无 merge(currentTracker 初始为 null)")
    class FirstCallNoMerge {

        // Given: 干净 AndActivity (currentTracker == null)
        // And:   t1 addObjectCriteria("k1", c1)
        // When:  and.enter(null, null, t1)
        // Then:  t1.map 不变 (无 merge 阶段)
        @Test
        @DisplayName("first call (currentTracker == null) 不触发 merge")
        void firstCallDoesNotTriggerMerge() throws Exception {
            BaseCriteria c1 = new Criteria();
            FactTracker t1 = new FactTracker();
            t1.addObjectCriteria("k1", c1);

            AndActivity and = andWithUnpassedPath();
            and.enter(null, null, t1);

            // 无 merge 阶段,所以 t1.map 应保持原状
            Map<Object, List<BaseCriteria>> map = t1.getObjectCriteriaMap();
            assertThat(map).hasSize(1);
            assertThat(map.get("k1")).containsExactly(c1);

            // 同时 currentTracker 已经被设置为 t1
            java.lang.reflect.Field f = AndActivity.class.getDeclaredField("currentTracker");
            f.setAccessible(true);
            assertThat(f.get(and)).isSameAs(t1);
        }
    }
}
