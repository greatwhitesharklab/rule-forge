package com.ruleforge.ir.drl;

import com.ruleforge.drl.DrlLexer;
import com.ruleforge.drl.DrlParser;
import com.ruleforge.ir.drl.LibraryParser.LibraryParseResult;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.45.2 — Library 加载 BFS 递归 + 环检测 BDD。
 *
 * <p>锁 2 件事:
 * <ol>
 *   <li>A → B → A 环 import 时,visited set 阻断,不无限循环</li>
 *   <li>嵌套 import(主 DRL 引用 A,A 引用 B,B 引用 C)→ 全部 type register 进 resolver,
 *       build 走完</li>
 * </ol>
 */
@DisplayName("V5.45.2 — Library 加载环检测 + 嵌套 BDD")
class LibraryLoadCycleTest {

    /**
     * V5.45.2 mock loader,根据 path → drl 文本 → LibraryParser.parseLibraryDrl 出 types
     * + innerImports。供 cycle / nested 测试用。
     */
    private static class InMemoryLibraryLoader implements LibraryLoader {
        final Map<String, String> table = new LinkedHashMap<>();
        final List<String> callOrder = new ArrayList<>();
        final Map<String, LibraryParseResult> results = new LinkedHashMap<>();

        void registerLibrary(String path, String drl) {
            table.put(path, drl);
            results.put(path, new LibraryParser().parseLibraryDrl(drl));
        }

        @Override
        public Map<String, DatatypeResolver.TypeInfo> loadLibrary(String libraryPath, String basePath) {
            callOrder.add(libraryPath);
            LibraryParseResult r = results.get(libraryPath);
            if (r == null) {
                return new LinkedHashMap<>();
            }
            return r.types();
        }
    }

    /**
     * V5.45.2 BFS 主逻辑(跟 KnowledgeBuilder 一致,只 mock loader 不同)。
     * 接受 library 内部 import,递归 BFS,visited set 阻断循环。
     *
     * <p>流程:loader.loadLibrary 返 types,loader 同时暴露 innerImports(path) 供
     * BFS 拓展(标准 loader SPI 不暴露 innerImports,这是 test-only 旁路)。
     */
    private static DatatypeResolver bfsLoad(String mainDrl, String basePath, InMemoryLibraryLoader loader) {
        DatatypeResolver resolver = new DatatypeResolver();
        DrlAstVisitor phase1 = new DrlAstVisitor(resolver, true);
        phase1.visit(new DrlParser(new CommonTokenStream(
            new DrlLexer(CharStreams.fromString(mainDrl)))).compilationUnit());

        Deque<String> queue = new ArrayDeque<>(phase1.getImports());
        Set<String> visited = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            String path = queue.poll();
            if (!visited.add(path)) continue;
            // 真实调 loader(记录 callOrder)— 然后从 cache 拿 innerImports
            Map<String, DatatypeResolver.TypeInfo> types = loader.loadLibrary(path, basePath);
            if (types.isEmpty() && !loader.table.containsKey(path)) continue;
            for (Map.Entry<String, DatatypeResolver.TypeInfo> e : types.entrySet()) {
                if (!resolver.isKnown(e.getKey())) {
                    resolver.register(e.getKey(), e.getValue());
                }
            }
            LibraryParseResult r = loader.results.get(path);
            if (r != null) queue.addAll(r.innerImports());
        }
        return resolver;
    }

    // ============================================================
    // === BDD 1 — 环 import 阻断 ===
    // ============================================================

    @Nested
    @DisplayName("Given A → B → A 环 import,When BFS load,Then visited set 阻断 不死循环")
    class CycleDetection {

        @Test
        @DisplayName("主 DRL import A,A import B,B import A → loader.callOrder 含 A 和 B 各 1 次,不死循环")
        void cycle() {
            String libA =
                "import \"libs/b.drl\";\n"
                + "declare TypeA\n    fieldA : String\nend\n";
            String libB =
                "import \"libs/a.drl\";\n"
                + "declare TypeB\n    fieldB : String\nend\n";
            String mainDrl =
                "import \"libs/a.drl\";\n"
                + "rule \"R1\" when TypeA() then end\n";

            InMemoryLibraryLoader loader = new InMemoryLibraryLoader();
            loader.registerLibrary("libs/a.drl", libA);
            loader.registerLibrary("libs/b.drl", libB);

            DatatypeResolver resolver = bfsLoad(mainDrl, "/test", loader);

            // TypeA + TypeB 都 register 了
            assertTrue(resolver.isKnown("TypeA"));
            assertTrue(resolver.isKnown("TypeB"));
            // 环阻断:loader 实际只调了 2 次(a 一次,b 一次,a 不再被调)
            assertEquals(2, loader.callOrder.size(),
                "loader called once per unique library path (cycle broken): " + loader.callOrder);
            assertEquals(1, loader.callOrder.stream().filter(p -> p.equals("libs/a.drl")).count(),
                "a.drl called exactly once");
        }
    }

    // ============================================================
    // === BBD 2 — 嵌套 import 全部 register ===
    // ============================================================

    @Nested
    @DisplayName("Given 嵌套 import A → B → C,When BFS load,Then 全部 type register")
    class NestedImports {

        @Test
        @DisplayName("主 DRL import A → A import B → B import C → 3 个 type 全部 resolver 命中")
        void nested() {
            String libA =
                "import \"libs/b.drl\";\n"
                + "declare TypeA\n    fieldA : String\nend\n";
            String libB =
                "import \"libs/c.drl\";\n"
                + "declare TypeB\n    fieldB : String\nend\n";
            String libC =
                "declare TypeC\n    fieldC : String\nend\n";
            String mainDrl =
                "import \"libs/a.drl\";\n"
                + "rule \"R1\" when TypeA() then end\n";

            InMemoryLibraryLoader loader = new InMemoryLibraryLoader();
            loader.registerLibrary("libs/a.drl", libA);
            loader.registerLibrary("libs/b.drl", libB);
            loader.registerLibrary("libs/c.drl", libC);

            DatatypeResolver resolver = bfsLoad(mainDrl, "/test", loader);

            assertTrue(resolver.isKnown("TypeA"), "TypeA registered");
            assertTrue(resolver.isKnown("TypeB"), "TypeB registered (BFS depth 2)");
            assertTrue(resolver.isKnown("TypeC"), "TypeC registered (BFS depth 3)");
            assertEquals(3, loader.callOrder.size(),
                "all 3 libraries loaded in BFS order: " + loader.callOrder);
        }
    }
}
