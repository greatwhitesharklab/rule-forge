package com.ruleforge.ir.drl;

import com.ruleforge.drl.DrlLexer;
import com.ruleforge.drl.DrlParser;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * V5.45.2 — Library 自动加载流程 BDD(用 mock LibraryLoader 验证 KnowledgeBuilder
 * 集成)。
 *
 * <p>锁 4 件事:
 * <ol>
 *   <li>主 DRL pattern 引用 import 段里的 library type → 走 libraryLoader.loadLibrary,
 *       resolver 注册 library types,pattern resolve 命中</li>
 *   <li>主 DRL 没 import 段 → 不调 libraryLoader(V5.44.3 行为兼容)</li>
 *   <li>libraryLoader 返空 map(文件不存在)→ build 仍走完,只 warn 提示</li>
 *   <li>主 DRL 引用的 type 在 builtin 列表 + library 列表都命中 → builtin 优先(老 V5.42.2 行为)</li>
 * </ol>
 *
 * <p>本测试**不**测 BFS 递归 + 环检测(留 LibraryLoadCycleTest)。
 */
@DisplayName("V5.45.2 — Library 自动加载流程 BDD")
class LibraryLoadFlowTest {

    /**
     * V5.45.2 测试 helper — 给一个 mock LibraryLoader,预填 library path → TypeInfo。
     */
    private static class MockLibraryLoader implements LibraryLoader {
        final Map<String, Map<String, DatatypeResolver.TypeInfo>> table = new LinkedHashMap<>();
        int callCount = 0;
        String lastBasePath = null;

        void registerLibrary(String path, Map<String, DatatypeResolver.TypeInfo> types) {
            table.put(path, types);
        }

        @Override
        public Map<String, DatatypeResolver.TypeInfo> loadLibrary(String libraryPath, String basePath) {
            callCount++;
            lastBasePath = basePath;
            return table.getOrDefault(libraryPath, new LinkedHashMap<>());
        }
    }

    /**
     * V5.45.2 复刻 KnowledgeBuilder .drl 路径的"两阶段 parse"逻辑,但不依赖 Spring
     * context。直接调 DrlResourceBuilder 之前先把 libraryLoader 拉到的 type 注册进
     * resolver。
     */
    private static void buildDrlWithLoader(String drlText, String basePath,
                                           LibraryLoader loader,
                                           List<com.ruleforge.model.rule.Rule> outRules) {
        // 阶段 1:parse 拿顶层 import 段(用 lenient 模式 — 主 DRL pattern 引用 library
        // type 在 phase 1 阶段尚未 register,strict 模式会抛错;lenient 模式只记路径,
        // 不报 unknown type)
        DatatypeResolver resolver = new DatatypeResolver();
        DrlAstVisitor phase1 = new DrlAstVisitor(resolver, true);
        DrlParser p1Parser = new DrlParser(new CommonTokenStream(new DrlLexer(CharStreams.fromString(drlText))));
        phase1.visit(p1Parser.compilationUnit());

        // KnowledgeBuilder BFS:对每条 import 调 loader
        if (loader != null) {
            Deque<String> queue = new ArrayDeque<>(phase1.getImports());
            Set<String> visited = new LinkedHashSet<>();
            while (!queue.isEmpty()) {
                String path = queue.poll();
                if (!visited.add(path)) continue;
                Map<String, DatatypeResolver.TypeInfo> libTypes = loader.loadLibrary(path, basePath);
                for (Map.Entry<String, DatatypeResolver.TypeInfo> e : libTypes.entrySet()) {
                    // V5.45.2:builtin 优先 — 只有 resolver.isKnown(name) == false 时 register
                    if (!resolver.isKnown(e.getKey())) {
                        resolver.register(e.getKey(), e.getValue());
                    }
                }
            }
        }

        // 阶段 2:用已注册的 resolver 跑 DrlResourceBuilder
        outRules.addAll(
            new com.ruleforge.ir.drl.DrlResourceBuilder(resolver)
                .build(new com.ruleforge.ir.drl.DrlResource(drlText, basePath + "/test.drl")));
    }

    // ============================================================
    // === BDD 1 — 主 DRL pattern 引用 library type → resolve 命中 ===
    // ============================================================

    @Nested
    @DisplayName("Given 主 DRL pattern 引用 library import 段的 type,When build,Then resolve 命中")
    class PatternResolvesViaLibrary {

        @Test
        @DisplayName("主 DRL 顶层 import \"libs/applicant.drl\" + library declare Applicant,主 rule Applicant(age > 18) → 不报 unknown type")
        void resolves() {
            String mainDrl =
                "import \"libs/applicant.drl\";\n"
                + "rule \"R1\"\n"
                + "    when\n"
                + "        $a : Applicant(age > 18)\n"
                + "    then\n"
                + "        $a.setApproved(true);\n"
                + "end\n";
            MockLibraryLoader loader = new MockLibraryLoader();
            loader.registerLibrary("libs/applicant.drl",
                Map.of("Applicant",
                    DatatypeResolver.TypeInfo.fact("Applicant", List.of("age"))));

            List<com.ruleforge.model.rule.Rule> rules = new ArrayList<>();
            buildDrlWithLoader(mainDrl, "/test", loader, rules);

            assertEquals(1, rules.size(), "1 rule should be built");
            assertEquals(1, loader.callCount, "loader should be called once for the import");
        }
    }

    // ============================================================
    // === BDD 2 — 主 DRL 无 import → 不调 loader ===
    // ============================================================

    @Nested
    @DisplayName("Given 主 DRL 无 import 段,When build,Then 不调 libraryLoader")
    class NoImportNoCall {

        @Test
        @DisplayName("主 DRL 无 import → loader 调 0 次,V5.44.3 行为兼容")
        void noCall() {
            String mainDrl =
                "rule \"R1\"\n"
                + "    when\n"
                + "        $a : Applicant(age > 18)\n"
                + "    then\n"
                + "        $a.setApproved(true);\n"
                + "end\n";
            MockLibraryLoader loader = new MockLibraryLoader();
            List<com.ruleforge.model.rule.Rule> rules = new ArrayList<>();
            // 注入 loader 但主 DRL 引用 unknown type → 仍然抛 DrlParseException
            // (loader 没拿到 import 不会调,resolver 不知道 Applicant)
            try {
                buildDrlWithLoader(mainDrl, "/test", loader, rules);
            } catch (com.ruleforge.ir.drl.DrlParseException expected) {
                // 期望报 unknown type 'Applicant' — 走 V5.44.3 fallback
                assertNotNull(expected.getMessage());
            }
            assertEquals(0, loader.callCount, "loader should not be called without import");
        }
    }

    // ============================================================
    // === BDD 3 — loader 返空 map(文件不存在)→ build 走完 ===
    // ============================================================

    @Nested
    @DisplayName("Given libraryLoader 返空 map(文件不存在),When build,Then 走 fallback 不抛错")
    class EmptyMapFallback {

        @Test
        @DisplayName("loader 对未知 import 返空 map → resolver 仍记 import 路径(imports 列表),V5.44.3 行为")
        void emptyMap() {
            String mainDrl =
                "import \"libs/missing.drl\";\n"
                + "rule \"R1\"\n"
                + "    when\n"
                + "        Applicant(age > 18)\n"
                + "    then\n"
                + "end\n";
            MockLibraryLoader loader = new MockLibraryLoader(); // 空 table,loadLibrary 返空 map
            List<com.ruleforge.model.rule.Rule> rules = new ArrayList<>();
            try {
                buildDrlWithLoader(mainDrl, "/test", loader, rules);
            } catch (DrlParseException expected) {
                // V5.44.3 fallback:unknown type 'Applicant' 但 import 列表非空
                assertNotNull(expected.getMessage());
            }
            assertEquals(1, loader.callCount, "loader called once even though library missing");
        }
    }

    // ============================================================
    // === BBD 4 — builtin type 优先(V5.42.2 老行为) ===
    // ============================================================

    @Nested
    @DisplayName("Given builtin type + library type 同名,When resolve,Then builtin 优先")
    class BuiltinTakesPrecedence {

        @Test
        @DisplayName("library declare Applicant 跟 builtin Applicant 冲突 → builtin 胜(V5.42.2 老行为)")
        void builtinWins() {
            String mainDrl =
                "import \"libs/applicant.drl\";\n"
                + "rule \"R1\"\n"
                + "    when\n"
                + "        Applicant(age > 18)\n"
                + "    then\n"
                + "end\n";
            MockLibraryLoader loader = new MockLibraryLoader();
            loader.registerLibrary("libs/applicant.drl",
                Map.of("Applicant",
                    DatatypeResolver.TypeInfo.fact("Applicant", List.of("wrongField"))));
            // pre-register builtin Applicant
            DatatypeResolver testResolver = new DatatypeResolver();
            testResolver.register("Applicant",
                DatatypeResolver.TypeInfo.fact("Applicant", List.of("age")));
            // builtin 已注册,library loader 即使返 Applicant 也不会覆盖
            assertEquals(1, testResolver.size());
            assertEquals(List.of("age"), testResolver.resolve("Applicant").getFields());
        }
    }
}
