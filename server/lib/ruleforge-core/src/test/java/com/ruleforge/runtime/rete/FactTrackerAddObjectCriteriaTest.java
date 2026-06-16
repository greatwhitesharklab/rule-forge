package com.ruleforge.runtime.rete;

import com.ruleforge.model.rule.lhs.BaseCriteria;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.rete.test.EngineContextWirer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.97 — {@link FactTracker#addObjectCriteria(Object, BaseCriteria)} 契约 BDD。
 *
 * <p>锁 V5.97 修法(用 {@code get + null check} 替代 {@code containsKey + get}
 * 双 lookup)的行为不变性:
 * <ul>
 *   <li>{@code addObjectCriteria(obj, c)} 第一次 → 创建 list 装 c</li>
 *   <li>同一 obj 第二次 add 不同 c → 追加到 list(不覆盖)</li>
 *   <li>同一 obj 同一 c 重复 add → dedup,list 大小不变(契约保留)</li>
 *   <li>{@link HashMap} 实例走特殊 path(转成 HashMap.class.getName() 作 key,跟老逻辑兼容)</li>
 *   <li>{@code newSubFactTracker} 复制 objectCriteriaMap 共享</li>
 * </ul>
 *
 * <p><b>Why V5.97 选这条</b>:沿 V5.93 立的"砍 HashMap.containsKey + get 双 lookup"原则,
 * {@code FactTracker.addObjectCriteria} 是同样的反模式:
 * <pre>
 * if (objectCriteriaMap.containsKey(obj)) {       // lookup 1
 *     List<BaseCriteria> list = objectCriteriaMap.get(obj);  // lookup 2
 *     ...
 * }
 * </pre>
 * 跟 V5.93 {@code EvaluationContextImpl.getCriteriaValue} 和 V5.94 {@code Criteria.getPartValue}
 * 同一反模式,1:1 套 V5.93 修法即可:
 * <pre>
 * List<BaseCriteria> list = objectCriteriaMap.get(obj);  // lookup 1
 * if (list != null) {
 *     // 已有 list
 * } else {
 *     // 新建 + put
 * }
 * </pre>
 * 省 1 HashMap.containsKey + 1 String.hashCode per call。
 *
 * <p><b>行为等价性 audit</b>:{@code HashMap.get} 对 "key 不存在" 和 "key 存在但 null 值"
 * 都返 null,跟 {@code containsKey} 行为等价(在 list 场景下 put 后 list 永远非 null,
 * 所以 null-stored 路径不可达 — 跟 V5.93/V5.94 相比,本 PR 无 null-stored 风险)。
 *
 * @see com.ruleforge.docs.notes.v597-facttracker-double-lookup V5.97 完整 doc
 * @since 5.97
 */
@DisplayName("V5.97 — FactTracker.addObjectCriteria 行为契约 (修双 lookup)")
class FactTrackerAddObjectCriteriaTest {

    private FactTracker tracker;
    private BaseCriteria criteria1;
    private BaseCriteria criteria2;

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    @BeforeEach
    void setUp() {
        tracker = new FactTracker();
        criteria1 = new Criteria();
        criteria2 = new Criteria();
    }

    @Nested
    @DisplayName("first add 创建新 list")
    class FirstAddCreatesList {

        // Given 干净 FactTracker
        // When addObjectCriteria("fact1", c1)
        // Then objectCriteriaMap 含 "fact1" → [c1]
        @Test
        @DisplayName("first add 创建新 list 装 criteria")
        void firstAddCreatesList() {
            tracker.addObjectCriteria("fact1", criteria1);
            Map<Object, List<BaseCriteria>> map = tracker.getObjectCriteriaMap();
            assertThat(map).hasSize(1);
            assertThat(map.get("fact1")).containsExactly(criteria1);
        }
    }

    @Nested
    @DisplayName("同 obj 二次 add 不同 criteria → 追加")
    class SecondAddAppends {

        // Given addObjectCriteria("fact1", c1)
        // When  addObjectCriteria("fact1", c2)
        // Then "fact1" → [c1, c2]
        @Test
        @DisplayName("同 obj 二次 add 不同 criteria → 追加到 list")
        void secondAddAppendsNewCriteria() {
            tracker.addObjectCriteria("fact1", criteria1);
            tracker.addObjectCriteria("fact1", criteria2);
            Map<Object, List<BaseCriteria>> map = tracker.getObjectCriteriaMap();
            assertThat(map).hasSize(1);
            assertThat(map.get("fact1")).containsExactly(criteria1, criteria2);
        }
    }

    @Nested
    @DisplayName("同 obj 同 criteria 重复 add → dedup (契约保留)")
    class DuplicateAddDedup {

        // Given addObjectCriteria("fact1", c1)
        // When  addObjectCriteria("fact1", c1) (重复)
        // Then "fact1" → [c1] (size=1,重复不增加)
        @Test
        @DisplayName("同 obj 同 criteria 重复 add → list 大小不变(去重契约)")
        void duplicateAddIsDedup() {
            tracker.addObjectCriteria("fact1", criteria1);
            tracker.addObjectCriteria("fact1", criteria1);
            Map<Object, List<BaseCriteria>> map = tracker.getObjectCriteriaMap();
            assertThat(map).hasSize(1);
            assertThat(map.get("fact1")).containsExactly(criteria1);
        }
    }

    @Nested
    @DisplayName("HashMap 实例走特殊 key 路径")
    class HashMapInstancePath {

        // Given HashMap 实例
        // When addObjectCriteria(hashMapInstance, c1)
        // Then 内部 map 用 HashMap.class.getName() 作 key
        @Test
        @DisplayName("HashMap 实例 → key 走 HashMap.class.getName() (跟老逻辑兼容)")
        void hashMapInstanceKeyCanonicalization() {
            HashMap<String, Object> hashMapInstance = new HashMap<>();
            tracker.addObjectCriteria(hashMapInstance, criteria1);
            Map<Object, List<BaseCriteria>> map = tracker.getObjectCriteriaMap();
            assertThat(map).hasSize(1);
            // 走特殊 path 后 key 是 class name String
            assertThat(map).containsKey(HashMap.class.getName());
            assertThat(map.get(HashMap.class.getName())).containsExactly(criteria1);
        }
    }

    @Nested
    @DisplayName("newSubFactTracker 复制 map 共享")
    class NewSubFactTracker {

        // Given tracker 已 add ("fact1", c1) + ("fact2", c2)
        // When newSubFactTracker()
        // Then sub 的 map 含相同 2 entries(且修改 sub 不影响 parent)
        @Test
        @DisplayName("newSubFactTracker 复制 objectCriteriaMap(初始相同,后续独立)")
        void newSubFactTrackerCopiesMap() {
            tracker.addObjectCriteria("fact1", criteria1);
            tracker.addObjectCriteria("fact2", criteria2);

            FactTracker sub = tracker.newSubFactTracker();
            assertThat(sub.getObjectCriteriaMap()).hasSize(2);
            assertThat(sub.getObjectCriteriaMap().get("fact1")).containsExactly(criteria1);
            assertThat(sub.getObjectCriteriaMap().get("fact2")).containsExactly(criteria2);
        }
    }
}
