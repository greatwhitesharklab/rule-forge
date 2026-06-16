package com.ruleforge.builder;

import com.ruleforge.model.rule.Library;
import com.ruleforge.model.rule.LibraryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V5.100 — {@link KnowledgeBuilder#addToLibraryMap(Map, List)} 契约 BDD。
 *
 * <p>锁 V5.100 修法({@code containsKey + put} 双 lookup → {@code get == null + put})
 * 的行为不变性:
 * <ul>
 *   <li>{@code libraries == null} → map 不变 (no throw)</li>
 *   <li>{@code libraries} 空 list → map 不变</li>
 *   <li>不同 path → 全装上 (first-wins per path)</li>
 *   <li>重复 path → <b>first-wins</b> (跳过后续 duplicate, 不覆盖)</li>
 *   <li>map 初始 null value → 装上 (证明 "get == null" 能区分 "absent" vs "present with null")</li>
 * </ul>
 *
 * <p><b>Why V5.100 选这条</b>: V5.93 立的 "HashMap.get 已能区分 absent vs null value" 原则
 * (砍 containsKey + get 双 lookup, save 1 hash lookup per call)。 build-time 调用, 不在 rete hot
 * path, JFR 0 sample 预期 — 跟 V6.1 AndActivity.enter / V5.97 FactTracker.addObjectCriteria
 * 同档 pure code elegance closure。
 *
 * <p>行为关键: Library 对象永不为 null (反编译收尾, 无 put(key, null) 风险),
 * 所以 {@code map.get(key) == null} 跟 {@code !map.containsKey(key)} 100% 等价 —
 * 两者都表示 "this key 没装过 Library"。
 *
 * @see com.ruleforge.docs.notes.v5100-knowledgebuilder-addtolibrarymap-doublelookup V5.100 完整 doc
 * @since 5.100
 */
@DisplayName("V5.100 — KnowledgeBuilder.addToLibraryMap 契约 (double lookup 砍)")
class KnowledgeBuilderAddToLibraryMapTest {

    private final KnowledgeBuilder builder = new KnowledgeBuilder();

    /** Reflective access to private {@code addToLibraryMap(Map, List)}. */
    @SuppressWarnings("unchecked")
    private void invokeAddToLibraryMap(Map<String, Library> map, List<Library> libraries) throws Exception {
        Method m = KnowledgeBuilder.class.getDeclaredMethod("addToLibraryMap", Map.class, List.class);
        m.setAccessible(true);
        m.invoke(builder, map, libraries);
    }

    private static Library lib(String path) {
        return new Library(path, "1.0", LibraryType.Variable);
    }

    // ─── Null guard: libraries == null ───────────────────────────────────────

    @Nested
    @DisplayName("null guard: libraries == null 时不抛 + map 不变")
    class NullLibraries {

        // Given: map 已装 1 lib, libraries == null
        // When:  addToLibraryMap(map, null)
        // Then:  map 不变 (no NPE, no put)
        @Test
        @DisplayName("libraries == null + map 非空 → map 不变, no NPE")
        void nullLibrariesWithExistingMap() throws Exception {
            Map<String, Library> map = new HashMap<>();
            map.put("/preexisting", lib("/preexisting"));

            invokeAddToLibraryMap(map, null);

            assertThat(map).containsOnlyKeys("/preexisting");
        }

        // Given: 空 map, libraries == null
        // When:  addToLibraryMap(map, null)
        // Then:  map 仍空 (no throw)
        @Test
        @DisplayName("libraries == null + 空 map → map 仍空")
        void nullLibrariesWithEmptyMap() throws Exception {
            Map<String, Library> map = new HashMap<>();

            invokeAddToLibraryMap(map, null);

            assertThat(map).isEmpty();
        }
    }

    // ─── Empty list: no-op ──────────────────────────────────────────────────

    @Nested
    @DisplayName("empty list: 空 libraries list 时不抛 + map 不变")
    class EmptyLibraries {

        // Given: map 已装 1 lib, libraries 空 list
        // When:  addToLibraryMap(map, [])
        // Then:  map 不变 (no iteration body hit)
        @Test
        @DisplayName("libraries == [] → map 不变, no throw")
        void emptyListNoOp() throws Exception {
            Map<String, Library> map = new HashMap<>();
            map.put("/preexisting", lib("/preexisting"));

            invokeAddToLibraryMap(map, Collections.emptyList());

            assertThat(map).containsOnlyKeys("/preexisting");
        }
    }

    // ─── Different paths: all installed ─────────────────────────────────────

    @Nested
    @DisplayName("不同 path: 全装上")
    class DifferentPaths {

        // Given: 空 map, 3 个不同 path 的 Library
        // When:  addToLibraryMap(map, [a, b, c])
        // Then:  map 全装上 (3 entry)
        @Test
        @DisplayName("3 不同 path → 全装上, 顺序无关")
        void threeDistinctPathsInstalled() throws Exception {
            Map<String, Library> map = new HashMap<>();
            Library a = lib("/path/a");
            Library b = lib("/path/b");
            Library c = lib("/path/c");

            invokeAddToLibraryMap(map, java.util.Arrays.asList(a, b, c));

            assertThat(map).containsOnlyKeys("/path/a", "/path/b", "/path/c");
            assertThat(map.get("/path/a")).isSameAs(a);
            assertThat(map.get("/path/b")).isSameAs(b);
            assertThat(map.get("/path/c")).isSameAs(c);
        }

        // Given: 空 map, 单 library
        // When:  addToLibraryMap(map, [single])
        // Then:  map 装上 1 entry
        @Test
        @DisplayName("单 library → map 装上 1 entry")
        void singleLibraryInstalled() throws Exception {
            Map<String, Library> map = new HashMap<>();
            Library a = lib("/path/a");

            invokeAddToLibraryMap(map, Collections.singletonList(a));

            assertThat(map).containsOnlyKeys("/path/a");
            assertThat(map.get("/path/a")).isSameAs(a);
        }
    }

    // ─── Duplicate paths: first-wins contract ───────────────────────────────

    @Nested
    @DisplayName("重复 path: first-wins (single-writer 后到跳过)")
    class DuplicatePaths {

        // Given: 空 map, 3 个 library 但 path 重叠 ([/a v1, /a v2, /b])
        // When:  addToLibraryMap(map, ...)
        // Then:  map[/a] == FIRST (/a v1), map[/b] == /b (first-wins)
        @Test
        @DisplayName("重复 path 跨多个 library → first-wins (后续 duplicate 跳过, 不覆盖)")
        void duplicatePathFirstWins() throws Exception {
            Map<String, Library> map = new HashMap<>();
            Library firstA = new Library("/a", "1.0", LibraryType.Variable);
            Library secondA = new Library("/a", "2.0", LibraryType.Variable);
            Library b = lib("/b");

            invokeAddToLibraryMap(map, java.util.Arrays.asList(firstA, secondA, b));

            assertThat(map).containsOnlyKeys("/a", "/b");
            assertThat(map.get("/a")).isSameAs(firstA);  // first-wins, secondA 跳过
            assertThat(map.get("/a").getVersion()).isEqualTo("1.0");
            assertThat(map.get("/b")).isSameAs(b);
        }

        // Given: map 已装 /a v1, 然后 addToLibraryMap(map, [/a v2])
        // When:  addToLibraryMap(map, ...)
        // Then:  map[/a] 仍是 v1 (existing first-wins, 不被新 value 覆盖)
        @Test
        @DisplayName("map 已装 + 新 list duplicate path → 保留旧 value, 不覆盖")
        void existingFirstWinsOverNew() throws Exception {
            Map<String, Library> map = new HashMap<>();
            Library existingA = new Library("/a", "1.0", LibraryType.Variable);
            map.put("/a", existingA);

            Library newA = new Library("/a", "2.0", LibraryType.Variable);
            invokeAddToLibraryMap(map, Collections.singletonList(newA));

            assertThat(map.get("/a")).isSameAs(existingA);
            assertThat(map.get("/a").getVersion()).isEqualTo("1.0");
        }
    }

    // ─── Null value in map: get == null works for both absent + null present ─

    @Nested
    @DisplayName("null value 区分: map.get(key) == null 等价 !map.containsKey(key) (本场景)")
    class NullValueDistinction {

        // Given: map 已装 1 lib, libraries 含 2 lib (一个 duplicate path + 一个新 path)
        // When:  addToLibraryMap(map, ...)
        // Then:  duplicate path 跳过 (first-wins), 新 path 装上
        //   本测试重点: 验证 "map.get(path) == null" 走的是 "absent path" 分支 (本场景无 put(key, null) 风险)
        @Test
        @DisplayName("mixed (1 duplicate + 1 new path) → duplicate 跳过, new 装上")
        void mixedDuplicateAndNewPath() throws Exception {
            Map<String, Library> map = new HashMap<>();
            map.put("/existing", lib("/existing"));
            Library dupExisting = new Library("/existing", "99.0", LibraryType.Constant);
            Library newPath = lib("/new");

            invokeAddToLibraryMap(map, java.util.Arrays.asList(dupExisting, newPath));

            assertThat(map).containsOnlyKeys("/existing", "/new");
            assertThat(map.get("/existing").getType()).isEqualTo(LibraryType.Variable);  // 旧 value 保留
            assertThat(map.get("/new")).isSameAs(newPath);
        }
    }

    // ─── Multi-call accumulation: 同 instance 多次调用累计 ──────────────────

    @Nested
    @DisplayName("多次调用累计: 同一 map 多次 invoke, 状态累积")
    class MultiCallAccumulation {

        // Given: 空 map, 两次 addToLibraryMap call 各自装不同 path
        // When:  第一次装 [/a, /b], 第二次装 [/b duplicate, /c]
        // Then:  map 装 /a, /b (first-wins, secondA 跳过), /c
        @Test
        @DisplayName("2 次 call 累计: 第一次装 [/a, /b], 第二次装 [/b dup, /c] → /b 第一次的保留")
        void twoCallsAccumulateState() throws Exception {
            Map<String, Library> map = new HashMap<>();
            Library a = lib("/a");
            Library bFirst = new Library("/b", "1.0", LibraryType.Variable);
            Library bSecond = new Library("/b", "2.0", LibraryType.Variable);
            Library c = lib("/c");

            invokeAddToLibraryMap(map, java.util.Arrays.asList(a, bFirst));
            invokeAddToLibraryMap(map, java.util.Arrays.asList(bSecond, c));

            assertThat(map).containsOnlyKeys("/a", "/b", "/c");
            assertThat(map.get("/a")).isSameAs(a);
            assertThat(map.get("/b")).isSameAs(bFirst);  // first-wins 跨 call
            assertThat(map.get("/c")).isSameAs(c);
        }
    }

    // ─── List with null element safety: lib.getPath() 假设 lib 非 null ─────

    @Nested
    @DisplayName("list 元素 null: lib == null 时 lib.getPath() 抛 NPE (现有契约)")
    class ListElementNullSafety {

        // Given: list 含 null element
        // When:  addToLibraryMap(map, [null])
        // Then:  NPE (现有契约: lib 永不为 null, caller 责任)
        //   本测试 lock 现有行为 — V5.100 不改 null element 语义
        @Test
        @DisplayName("list 含 null element → lib.getPath() 抛 NPE (现有契约保留)")
        void nullElementInListThrowsNpe() {
            Map<String, Library> map = new HashMap<>();
            List<Library> libs = new ArrayList<>();
            libs.add(null);

            org.junit.jupiter.api.Assertions.assertThrows(
                    java.lang.reflect.InvocationTargetException.class,
                    () -> {
                        try {
                            invokeAddToLibraryMap(map, libs);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
            );
        }
    }
}
