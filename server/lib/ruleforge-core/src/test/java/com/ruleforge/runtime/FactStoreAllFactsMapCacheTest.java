package com.ruleforge.runtime;

import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.engine.KnowledgeSessionFactory;
import com.ruleforge.engine.KnowledgeSession;
import com.ruleforge.model.GeneralEntity;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Act;
import com.ruleforge.model.library.variable.CategoryType;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.library.variable.VariableLibrary;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.builder.ReteBuilder;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.rete.test.EngineContextWirer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.6 — FactStore.getAllFactsMap() 缓存失效 characterization test BDD。
 *
 * <p>锁 V6.6 优化 (缓存 + dirty flag) 的行为不变性 + 新 cache 契约:
 * <ul>
 *   <li><b>行为不变</b>:last-wins Map 视图, 同 className 后插覆盖前插 (跟 V5.82 修法一致)</li>
 *   <li><b>cache 命中</b>:连续两次调用返同一引用 (无 dirty 触发), 节省 HashMap 重建</li>
 *   <li><b>写入失效</b>:add / addAll / insert / remove / clear / reset 后下次调用触发重建</li>
 *   <li><b>外部只读</b>:loopRule / ValueCompute.findObject 等只调 .get(), 不会 mutate cache</li>
 * </ul>
 *
 * <p><b>Why V6.6 选这条</b>: {@code ValueCompute.findObject} 在 per-fact LHS 求值调
 * {@code getAllFactsMap()}, 这是 per-fact hot path. 旧实现每次调用都新建 HashMap + 遍历
 * allFactsList, 在大 N fact 下浪费. cache 命中后从 O(N) build 降到 O(1) get, per-fact 节省
 * HashMap 分配 + allFactsList 遍历.
 *
 * <p><b>Test fixture</b>: 直接 new FactStore 测 (无 KnowledgeSession / EngineContext 依赖).
 * 比 {@link com.ruleforge.rete.perf.AllFactsMapRetainsAllInsertsTest} 更低层.
 */
@DisplayName("V6.6 — FactStore.getAllFactsMap 缓存化契约")
class FactStoreAllFactsMapCacheTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        // SessionIntegration 测试需要 ReteBuilder 装配
        EngineContextWirer.wire();
    }

    @Nested
    @DisplayName("last-wins 行为不变性 (跟 V5.82 一致)")
    class LastWinsBehavior {

        // Given: 一个空 FactStore
        // When:  连续插入 2 个同 className fact
        // Then:  getAllFactsMap() 应只含最后插入的 fact (last-wins 视图)
        @Test
        @DisplayName("同 className 多次 insert 后 getAllFactsMap() 返最后 fact")
        void lastWinsForSameClass() {
            FactStore store = new FactStore();
            GeneralEntity u1 = new GeneralEntity("User");
            GeneralEntity u2 = new GeneralEntity("User");
            store.add(u1);
            store.add(u2);

            Map<String, Object> map = store.getAllFactsMap();
            assertThat(map).hasSize(1);
            assertThat(map).containsEntry("User", u2);
        }

        // Given: 一个空 FactStore
        // When:  插入 2 个不同 class fact
        // Then:  getAllFactsMap() 应含 2 个 entry, 各 class 1 个
        @Test
        @DisplayName("不同 class fact insert 后 getAllFactsMap() 返各 class 1 entry")
        void differentClassAllKept() {
            FactStore store = new FactStore();
            GeneralEntity u = new GeneralEntity("User");
            GeneralEntity a = new GeneralEntity("Address");
            store.add(u);
            store.add(a);

            Map<String, Object> map = store.getAllFactsMap();
            assertThat(map).hasSize(2);
            assertThat(map).containsEntry("User", u);
            assertThat(map).containsEntry("Address", a);
        }

        // Given: 一个空 FactStore, insert 1 个 fact
        // When:  insert 另一个 fact 后再 getAllFactsMap()
        // Then:  map 应反映 2 个 entry (新 entry 触发 cache 失效, 重建)
        @Test
        @DisplayName("insert 后 getAllFactsMap() 应反映新 fact (cache 失效)")
        void insertInvalidatesCache() {
            FactStore store = new FactStore();
            GeneralEntity u = new GeneralEntity("User");
            store.add(u);
            Map<String, Object> first = store.getAllFactsMap();
            assertThat(first).hasSize(1);

            GeneralEntity a = new GeneralEntity("Address");
            store.add(a);
            Map<String, Object> second = store.getAllFactsMap();
            assertThat(second).hasSize(2);
            assertThat(second).containsEntry("User", u);
            assertThat(second).containsEntry("Address", a);
        }
    }

    @Nested
    @DisplayName("cache 命中契约 — 多次连续调用返同一引用")
    class CacheHit {

        // Given: 一个 FactStore + 已 insert fact + 已 getAllFactsMap() 一次 (cache 已建)
        // When:  连续调 100 次 getAllFactsMap() 不写
        // Then:  所有调用应返同一引用 (Object.equals == true)
        @Test
        @DisplayName("连续多次调用应返同一引用 (cache 命中, 不重建)")
        void multipleReadsReturnSameReference() {
            FactStore store = new FactStore();
            store.add(new GeneralEntity("User"));

            Map<String, Object> first = store.getAllFactsMap();
            Map<String, Object> same = store.getAllFactsMap();
            for (int i = 0; i < 100; i++) {
                same = store.getAllFactsMap();
            }

            assertThat(same).isSameAs(first);
        }

        // Given: 一个 FactStore + 已 insert fact
        // When:  第 1 次 getAllFactsMap() 触发 build (dirty)
        // Then:  第 2 次应返同一引用 (cache 命中)
        @Test
        @DisplayName("首次调用 build, 后续调用复用 cache")
        void firstBuildThenReuse() {
            FactStore store = new FactStore();
            store.add(new GeneralEntity("User"));

            // 第 1 次: dirty → rebuild
            Map<String, Object> first = store.getAllFactsMap();
            assertThat(first).hasSize(1);

            // 第 2 次: cache 命中 → 同一引用
            Map<String, Object> second = store.getAllFactsMap();
            assertThat(second).isSameAs(first);
        }
    }

    @Nested
    @DisplayName("写入触发 cache 失效")
    class WriteInvalidates {

        // Given: 一个 FactStore, 已 insert fact + getAllFactsMap() (cache 已建)
        // When:  add 新 fact
        // Then:  下次 getAllFactsMap() 应是新引用 (cache 失效)
        @Test
        @DisplayName("add() 后 getAllFactsMap() 应是新引用")
        void addInvalidatesCache() {
            FactStore store = new FactStore();
            store.add(new GeneralEntity("User"));
            Map<String, Object> before = store.getAllFactsMap();

            store.add(new GeneralEntity("Address"));
            Map<String, Object> after = store.getAllFactsMap();

            assertThat(after).isNotSameAs(before);
            assertThat(after).hasSize(2);
        }

        // Given: 一个 FactStore, 已 insert fact + getAllFactsMap() (cache 已建)
        // When:  addAll 多个 fact
        // Then:  下次 getAllFactsMap() 应是新引用
        @Test
        @DisplayName("addAll() 后 getAllFactsMap() 应是新引用")
        void addAllInvalidatesCache() {
            FactStore store = new FactStore();
            store.add(new GeneralEntity("User"));
            Map<String, Object> before = store.getAllFactsMap();

            List<Object> more = new ArrayList<>();
            more.add(new GeneralEntity("Address"));
            more.add(new GeneralEntity("Order"));
            store.addAll(more);
            Map<String, Object> after = store.getAllFactsMap();

            assertThat(after).isNotSameAs(before);
            assertThat(after).hasSize(3);
        }

        // Given: 一个 FactStore, 已 insert 2 fact + getAllFactsMap()
        // When:  remove 一个 fact
        // Then:  下次 getAllFactsMap() 应是新引用 + 反映剩余 fact
        @Test
        @DisplayName("remove() 后 getAllFactsMap() 应是新引用")
        void removeInvalidatesCache() {
            FactStore store = new FactStore();
            GeneralEntity u = new GeneralEntity("User");
            GeneralEntity a = new GeneralEntity("Address");
            store.add(u);
            store.add(a);
            Map<String, Object> before = store.getAllFactsMap();

            store.remove(u);
            Map<String, Object> after = store.getAllFactsMap();

            assertThat(after).isNotSameAs(before);
            assertThat(after).hasSize(1);
            assertThat(after).containsEntry("Address", a);
            assertThat(after).doesNotContainKey("User");
        }

        // Given: 一个 FactStore, 已 insert fact + getAllFactsMap()
        // When:  clear() 全部
        // Then:  下次 getAllFactsMap() 应是空 map 新引用
        @Test
        @DisplayName("clear() 后 getAllFactsMap() 应是新引用 + 空")
        void clearInvalidatesCache() {
            FactStore store = new FactStore();
            store.add(new GeneralEntity("User"));
            Map<String, Object> before = store.getAllFactsMap();

            store.clear();
            Map<String, Object> after = store.getAllFactsMap();

            assertThat(after).isNotSameAs(before);
            assertThat(after).isEmpty();
        }
    }

    @Nested
    @DisplayName("insert() 路径: GeneralEntity 进 allFactsList, Map 型 fact 进 factMaps")
    class InsertInvalidates {

        // Given: 一个 FactStore, 已 insert GeneralEntity + getAllFactsMap()
        // When:  insert 另一个 GeneralEntity
        // Then:  下次 getAllFactsMap() 应是新引用 (cache 失效)
        @Test
        @DisplayName("insert(GeneralEntity) 后 getAllFactsMap() 应是新引用")
        void insertGeneralEntityInvalidatesCache() {
            FactStore store = new FactStore();
            store.add(new GeneralEntity("User"));
            Map<String, Object> before = store.getAllFactsMap();

            store.insert(new GeneralEntity("Address"));
            Map<String, Object> after = store.getAllFactsMap();

            assertThat(after).isNotSameAs(before);
            assertThat(after).hasSize(2);
        }

        // Given: 一个 FactStore, 已 insert GeneralEntity + getAllFactsMap()
        // When:  insert 一个 Map 型 fact (走 factMaps 分支, 不进 allFactsList)
        // Then:  下次 getAllFactsMap() 应返同引用 (Map 型 fact 不进 allFactsList, cache 不需要失效)
        @Test
        @DisplayName("insert(Map) 走 factMaps 分支, allFactsMap cache 不需要失效 (返同引用)")
        void insertMapDoesNotInvalidateCache() {
            FactStore store = new FactStore();
            store.add(new GeneralEntity("User"));
            Map<String, Object> before = store.getAllFactsMap();

            // Map 型 fact 不进 allFactsList (走 factMaps 分支)
            java.util.Map<String, Object> mapFact = new java.util.HashMap<>();
            mapFact.put("foo", "bar");
            store.insert(mapFact);
            Map<String, Object> after = store.getAllFactsMap();

            // Map 型 fact 不影响 allFactsList → allFactsMap cache 仍命中
            assertThat(after).isSameAs(before);
            assertThat(after).hasSize(1);
        }
    }

    @Nested
    @DisplayName("KnowledgeSession.getAllFactsMap() 走 cache")
    class SessionIntegration {

        // Given: 一个 KnowledgeSession, insert 1 个 fact
        // When:  连续 getAllFactsMap() 多次
        // Then:  多次调用返同一引用 (cache 命中)
        @Test
        @DisplayName("KnowledgeSession.getAllFactsMap() 多次调用应返同一引用")
        void sessionGetAllFactsMapReusesCache() {
            // 同 AllFactsMapRetainsAllInsertsTest 的 fixture 模式: 最小 KnowledgePackage
            Rule r = new Rule();
            r.setName("R0");
            r.setSalience(0);
            r.setLhs(new com.ruleforge.model.rule.lhs.Lhs());

            VariableLibrary lib = new VariableLibrary();
            VariableCategory cat = new VariableCategory();
            cat.setName("User");
            cat.setType(CategoryType.Clazz);
            cat.setClazz(GeneralEntity.class.getName());
            Variable v = new Variable();
            v.setName("name");
            v.setLabel("name");
            v.setType(Datatype.String);
            v.setAct(Act.In);
            cat.setVariables(Collections.singletonList(v));
            lib.addVariableCategory(cat);
            ResourceLibrary rl = new ResourceLibrary(
                Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());

            Rete rete = new ReteBuilder().buildRete(Collections.singletonList(r), rl);
            KnowledgePackage pkg = new KnowledgeBase(rete).getKnowledgePackage();
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(pkg);
            session.insert(new GeneralEntity("User"));

            Map<String, Object> first = session.getAllFactsMap();
            Map<String, Object> same = session.getAllFactsMap();
            for (int i = 0; i < 50; i++) {
                same = session.getAllFactsMap();
            }

            assertThat(same).isSameAs(first);
        }
    }
}