package com.ruleforge.decision.flow.engine;

import com.ruleforge.model.GeneralEntity;
import com.ruleforge.engine.KnowledgeSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * V5.39 A1 — ReteSession 行为规范。
 *
 * <p>3 BDD:初始空 / 替换 session / insertedEntities 累加。
 *
 * <p>本测试用 Mockito mock {@link KnowledgeSession}(接口方法多,手写 stub
 * 易漏);RE 引擎行为留给 {@code RuleNodeExecutorTest} 覆盖。
 */
@DisplayName("ReteSession 行为")
class ReteSessionTest {

    private static GeneralEntity fakeEntity(String name) {
        // GeneralEntity(String targetClass) — HashMap 子类
        return new GeneralEntity("com.ruleforge.Fake" + name);
    }

    @Nested
    @DisplayName("Group 1 — 初始空")
    class InitialState {

        @Test
        @DisplayName("Given 新建,Then session=null + insertedEntities 非 null 空 list")
        void fresh_is_null_session_and_empty_entities() {
            ReteSession rs = new ReteSession();
            assertNull(rs.getSession());
            assertNotNull(rs.getInsertedEntities());
            assertTrue(rs.getInsertedEntities().isEmpty());
        }
    }

    @Nested
    @DisplayName("Group 2 — 替换 session")
    class ReplaceSession {

        @Test
        @DisplayName("Given 已 set session,When replaceSession(新),Then session 切到新的")
        void replace_swaps_session_reference() {
            ReteSession rs = new ReteSession();
            KnowledgeSession s1 = mock(KnowledgeSession.class);
            KnowledgeSession s2 = mock(KnowledgeSession.class);
            rs.setSession(s1);
            assertSame(s1, rs.getSession());
            ReteSession returned = rs.replaceSession(s2);
            assertSame(s2, rs.getSession());
            // 链式风格:replace 返回 self
            assertSame(rs, returned);
        }

        @Test
        @DisplayName("Given replaceSession,Then insertedEntities 保留(已 insert 的事实不丢)")
        void replace_preserves_inserted_entities() {
            ReteSession rs = new ReteSession();
            rs.getInsertedEntities().add(fakeEntity("a"));
            rs.getInsertedEntities().add(fakeEntity("b"));
            int beforeSize = rs.getInsertedEntities().size();
            rs.replaceSession(mock(KnowledgeSession.class));
            assertEquals(beforeSize, rs.getInsertedEntities().size());
        }
    }

    @Nested
    @DisplayName("Group 3 — insertedEntities 累加")
    class InsertedEntitiesAccumulation {

        @Test
        @DisplayName("Given 添加 N 个 entity,When getInsertedEntities,Then 顺序保留 + size=N")
        void appended_entities_preserved_in_order() {
            ReteSession rs = new ReteSession();
            GeneralEntity a = fakeEntity("a");
            GeneralEntity b = fakeEntity("b");
            GeneralEntity c = fakeEntity("c");
            rs.getInsertedEntities().add(a);
            rs.getInsertedEntities().add(b);
            rs.getInsertedEntities().add(c);
            List<GeneralEntity> got = rs.getInsertedEntities();
            assertEquals(3, got.size());
            assertSame(a, got.get(0));
            assertSame(b, got.get(1));
            assertSame(c, got.get(2));
        }

        @Test
        @DisplayName("Given insertedEntities getter 返回内部 list 引用(非防御性拷贝 — 累加契约要求)")
        void inserted_entities_is_live_reference() {
            ReteSession rs = new ReteSession();
            // 同一引用,add 可见
            rs.getInsertedEntities().add(fakeEntity("x"));
            assertEquals(1, rs.getInsertedEntities().size());
            // 二次取值
            List<GeneralEntity> second = rs.getInsertedEntities();
            assertEquals(1, second.size());
            // 注:这是契约 — append-style,getter 返回 live ref。
            // 若 caller 改 list 元素,会直接影响 ReteSession(决策流内部可控)。
        }
    }
}
