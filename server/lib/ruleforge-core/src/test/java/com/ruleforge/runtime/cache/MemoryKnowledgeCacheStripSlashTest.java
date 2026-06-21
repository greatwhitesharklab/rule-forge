package com.ruleforge.runtime.cache;

import com.ruleforge.runtime.KnowledgePackage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * V6.9.27 — {@link MemoryKnowledgeCache} leading-slash normalization 契约 BDD。
 *
 * <p>锁 V6.9.27 收口 (5 method 重复 `if (id.startsWith("/")) id = id.substring(1);` →
 * private {@code stripLeadingSlash(String)} helper) 的行为不变性:
 * <ul>
 *   <li>5 entry point (getKnowledge/putKnowledge/markDirty/isDirty/clearDirty) 都对
 *       带或不带 leading slash 的 key 等价处理</li>
 *   <li>{@code removeKnowledge}/{@code removeKnowledgeByProjectName} 不带 slash 处理
 *       (保持原契约 — refactor 范围内不动)</li>
 * </ul>
 *
 * <p><b>Why V6.9.27</b>: 5 method 3 行 100% 同构, V6.9.14 helper extract 模式直接套;
 * 8 行净, pure refactor (无新 call site 加 strip — 避免引入 behavior change)。
 */
@DisplayName("V6.9.27 — MemoryKnowledgeCache stripLeadingSlash 契约")
class MemoryKnowledgeCacheStripSlashTest {

    private MemoryKnowledgeCache cache;
    private KnowledgePackage pkg;

    @BeforeEach
    void setUp() {
        cache = new MemoryKnowledgeCache();
        pkg = mock(KnowledgePackage.class);
        when(pkg.toString()).thenReturn("mock-pkg");
    }

    // ====== putKnowledge / getKnowledge ======

    @Nested
    @DisplayName("putKnowledge + getKnowledge")
    class PutGet {

        @Test
        @DisplayName("put '/foo' 后 get '/foo' 或 'foo' 都应返回同 package")
        void putWithSlashGetBoth() {
            cache.putKnowledge("/foo", pkg);
            assertThat(cache.getKnowledge("/foo")).isSameAs(pkg);
            assertThat(cache.getKnowledge("foo")).isSameAs(pkg);
        }

        @Test
        @DisplayName("put 'foo' (no slash) 后 get '/foo' 或 'foo' 都应返回同 package")
        void putNoSlashGetBoth() {
            cache.putKnowledge("foo", pkg);
            assertThat(cache.getKnowledge("foo")).isSameAs(pkg);
            assertThat(cache.getKnowledge("/foo")).isSameAs(pkg);
        }

        @Test
        @DisplayName("put '/foo' 后 get 'bar' (不匹配) 应返 null")
        void getNonExistent() {
            cache.putKnowledge("/foo", pkg);
            assertThat(cache.getKnowledge("bar")).isNull();
            assertThat(cache.getKnowledge("/bar")).isNull();
        }
    }

    // ====== markKnowledgeDirty / isKnowledgeDirty / clearKnowledgeDirty ======

    @Nested
    @DisplayName("dirty flag 三件套")
    class DirtyFlag {

        @Test
        @DisplayName("markDirty '/foo' 后 isDirty '/foo' 或 'foo' 都返 true")
        void markSlashBothTrue() {
            cache.markKnowledgeDirty("/foo");
            assertThat(cache.isKnowledgeDirty("/foo")).isTrue();
            assertThat(cache.isKnowledgeDirty("foo")).isTrue();
        }

        @Test
        @DisplayName("未 markDirty 时 isDirty 返 false (default)")
        void notMarkedReturnsFalse() {
            assertThat(cache.isKnowledgeDirty("/foo")).isFalse();
            assertThat(cache.isKnowledgeDirty("foo")).isFalse();
        }

        @Test
        @DisplayName("clearDirty '/foo' 后 isDirty '/foo' 或 'foo' 都返 false")
        void clearSlashBothFalse() {
            cache.markKnowledgeDirty("/foo");
            assertThat(cache.isKnowledgeDirty("foo")).isTrue();
            cache.clearKnowledgeDirty("/foo");
            assertThat(cache.isKnowledgeDirty("/foo")).isFalse();
            assertThat(cache.isKnowledgeDirty("foo")).isFalse();
        }
    }

    // ====== removeKnowledge 不带 slash (契约保留) ======

    @Nested
    @DisplayName("removeKnowledge / removeKnowledgeByProjectName (V6.9.27 不动)")
    class RemovePath {

        @Test
        @DisplayName("removeKnowledge 是 literal remove, 不做 slash 归一化")
        void removeIsLiteral() {
            cache.putKnowledge("/foo", pkg);
            cache.removeKnowledge("/foo");  // literal "/foo" — 实际 map key 是 "foo"
            // 实际行为: V6.9.27 不改这个 path, 所以 "/foo" 找不到, "foo" 还在
            assertThat(cache.getKnowledge("foo")).isSameAs(pkg);
            assertThat(cache.getKnowledge("/foo")).isSameAs(pkg);
        }
    }
}
