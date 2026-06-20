package com.ruleforge.runtime;

import com.ruleforge.builder.KnowledgeBase;
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V6.8 — {@link KnowledgeSessionImpl#putKnowledgeSession(String, KnowledgeSession)} 行为契约 BDD。
 *
 * <p>锁 <b>silent bug fix</b>: 旧实现
 * <pre>
 * if (this.knowledgeSessionMap.containsKey(id)) {
 *     this.knowledgeSessionMap.put(id, session);
 * }
 * </pre>
 * 在调用方 {@code KnowledgeSessionFactory.L31-34} 的调用语义下
 * ({@code session == null} → 创建新会话并 put, 即 id <b>不存在</b> 时调 put)
 * → containsKey 永远 false → put 永远跳过 → <b>新子会话从未注册到 map</b>。
 * 这是反编译 artifact + 业务 bug 双重:put 的标准语义是"无论 key 是否存在都写入"。
 *
 * <p><b>修法</b>: 砍 containsKey guard,直接 {@code map.put(id, session)}。V5.93 原则套用:
 * 调用方已有 null check,这里的 containsKey 是冗余 TOCTOU。
 *
 * <p>本测试 3 个契约:
 * <ul>
 *   <li><b>put 新 id</b>: KnowledgeSessionFactory.L34 路径 — put 后 getKnowledgeSession 应返该 session</li>
 *   <li><b>put 覆盖已存在 id</b>: 二次 put 同 id 应覆盖前值 (last-wins, 标准 Map.put 语义)</li>
 *   <li><b>put 不影响其他 id</b>: 多 id 独立</li>
 * </ul>
 *
 * <p><b>Why V6.8 选这条</b>: 这是 silent bug — 813 测试都过但子会话注册路径实际从未生效。
 * 长期会造成"父子会话共享 state"的隐性未定义行为。修法跟 V5.93 原则 (containsKey+get →
 * get+null check) 100% 同构: 调用方已保证 absent 时才调,这里的 containsKey guard 是冗余。
 */
@DisplayName("V6.8 — KnowledgeSessionImpl.putKnowledgeSession 行为契约")
class KnowledgeSessionPutKnowledgeSessionTest {

    @BeforeAll
    static void wireEngineContext() throws Exception {
        EngineContextWirer.wire();
    }

    private static KnowledgeSessionImpl newSession() {
        // 最小 fixture: Foo rule, User category with name variable, 跟其他 V6.x test 同模式
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
        return new KnowledgeSessionImpl(pkg);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, KnowledgeSession> getSessionMap(KnowledgeSessionImpl session)
            throws Exception {
        // 反射访问 reteRegistry.knowledgeSessionMap (V6.4 字段迁移路径)
        Field regField = KnowledgeSessionImpl.class.getDeclaredField("reteRegistry");
        regField.setAccessible(true);
        ReteSessionRegistry registry = (ReteSessionRegistry) regField.get(session);
        return registry.getKnowledgeSessionMap();
    }

    @Nested
    @DisplayName("put 新 id — KnowledgeSessionFactory.L34 调用路径")
    class PutNewId {

        // Given: 一个 KnowledgeSession, 子会话 map 空
        // When:  putKnowledgeSession("id1", childSession)
        // Then:  map 应含 "id1" → childSession (V6.8 fix: 旧实现 skip put)
        @Test
        @DisplayName("put 新 id 后 map 应含该 id (V6.8 fix 旧实现 skip put)")
        void putNewIdRegistersInMap() throws Exception {
            KnowledgeSessionImpl parent = newSession();
            KnowledgeSessionImpl child = newSession();

            parent.putKnowledgeSession("id1", child);

            Map<String, KnowledgeSession> map = getSessionMap(parent);
            assertThat(map).containsEntry("id1", child);
        }

        // Given: 父 session + child1, 已 put "id1"
        // When:  getKnowledgeSession("id1")
        // Then:  应返 child1
        @Test
        @DisplayName("put 后 getKnowledgeSession 应能取回该 session")
        void getKnowledgeSessionReturnsPutValue() throws Exception {
            KnowledgeSessionImpl parent = newSession();
            KnowledgeSessionImpl child = newSession();

            parent.putKnowledgeSession("id1", child);
            KnowledgeSession retrieved = parent.getKnowledgeSession("id1");

            assertThat(retrieved).isSameAs(child);
        }

        // Given: 父 session, 子会话 map 空
        // When:  getKnowledgeSession("id1") (调前未 put)
        // Then:  应返 null
        @Test
        @DisplayName("未 put 的 id getKnowledgeSession 应返 null")
        void getUnregisteredIdReturnsNull() throws Exception {
            KnowledgeSessionImpl parent = newSession();
            assertThat(parent.getKnowledgeSession("id1")).isNull();
        }
    }

    @Nested
    @DisplayName("put 已存在 id — 覆盖语义 (last-wins)")
    class PutOverwritesExisting {

        // Given: 父 session, 已 put "id1" → child1
        // When:  putKnowledgeSession("id1", child2) 二次
        // Then:  map 应含 "id1" → child2 (覆盖, 标准 Map.put 语义)
        @Test
        @DisplayName("put 同 id 应覆盖前值 (last-wins)")
        void putSameIdOverwritesPrevious() throws Exception {
            KnowledgeSessionImpl parent = newSession();
            KnowledgeSessionImpl child1 = newSession();
            KnowledgeSessionImpl child2 = newSession();

            parent.putKnowledgeSession("id1", child1);
            parent.putKnowledgeSession("id1", child2);

            Map<String, KnowledgeSession> map = getSessionMap(parent);
            assertThat(map).containsEntry("id1", child2);
            assertThat(map).hasSize(1);
        }
    }

    @Nested
    @DisplayName("多 id 独立")
    class MultipleIdsIndependent {

        // Given: 父 session, 已 put id1, id2
        // When:  getKnowledgeSession id1, id2
        // Then:  各返各自 session
        @Test
        @DisplayName("多个 id 互不干扰")
        void multipleIdsAreIndependent() throws Exception {
            KnowledgeSessionImpl parent = newSession();
            KnowledgeSessionImpl child1 = newSession();
            KnowledgeSessionImpl child2 = newSession();

            parent.putKnowledgeSession("id1", child1);
            parent.putKnowledgeSession("id2", child2);

            assertThat(parent.getKnowledgeSession("id1")).isSameAs(child1);
            assertThat(parent.getKnowledgeSession("id2")).isSameAs(child2);
        }
    }
}
