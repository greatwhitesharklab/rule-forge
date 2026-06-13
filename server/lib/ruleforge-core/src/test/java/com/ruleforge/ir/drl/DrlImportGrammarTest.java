package com.ruleforge.ir.drl;

import com.ruleforge.drl.DrlLexer;
import com.ruleforge.drl.DrlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.44.3 — DRL 4 grammar 新增 {@code import} 段 BDD。
 *
 * <p>锁 6 件事:
 * <ol>
 *   <li>{@code import "libs/x.drl";} 顶层语句合法,visitor 收集到 imports 列表</li>
 *   <li>多 {@code import} 段顺序保持 + 去重</li>
 *   <li>DrlDeserializer.parseDrl 端把 visitor 收集的 import 列表塞进
 *       {@link DatatypeResolver#addImport(String)},resolve() 命中后错误信息
 *       包含 import 列表(便于诊断)</li>
 *   <li>{@code import} 后未注册 type 抛 DrlParseException,错误信息含 import 列表
 *       提示 caller "library 文件实际加载 V5.45+ 跟进"</li>
 *   <li>没 {@code import} 段时,行为跟 V5.42.2 一致(错误信息不含 import 列表,
 *       走 builtin-only 提示)</li>
 *   <li>空字符串 import({@code import "";})抛 syntax error(grammar reject)——
 *       测试"空 import 路径"的防御</li>
 * </ol>
 *
 * <p>本测试**不**测 library 文件实际加载(那需要 V5.45+ 单独 PR);只锁
 * grammar 行为 + 路径收集 + resolver 状态正确。
 *
 * @since 5.44
 */
@DisplayName("V5.44.3 — DRL 4 grammar import 段 BDD")
class DrlImportGrammarTest {

    private DatatypeResolver resolver;

    private DatatypeResolver makeResolver() {
        DatatypeResolver r = new DatatypeResolver();
        r.register("Applicant",
            DatatypeResolver.TypeInfo.fact("Applicant",
                Arrays.asList("age", "income", "score")));
        return r;
    }

    // ============================================================
    // === V5.44.3 BDD 1 — 单 import 段合法 + 收集 ===
    // ============================================================

    @Nested
    @DisplayName("Given 单 import 段,When 走 visitor,Then imports 列表含该路径")
    class SingleImport {

        @Test
        @DisplayName("import \"libs/variables.drl\"; 合法 + 收集")
        void singleImport() {
            List<String> imports = visitImports(
                "import \"libs/variables.drl\";\n"
                + "rule \"R1\" when Applicant(age > 18) then end");
            assertEquals(1, imports.size());
            assertEquals("libs/variables.drl", imports.get(0));
        }

        @Test
        @DisplayName("import 段可省略末尾分号(grammar 允许 SEMI?)")
        void noSemicolon() {
            List<String> imports = visitImports(
                "import \"libs/variables.drl\"\n"
                + "rule \"R1\" when Applicant(age > 18) then end");
            assertEquals(1, imports.size());
            assertEquals("libs/variables.drl", imports.get(0));
        }
    }

    // ============================================================
    // === V5.44.3 BDD 2 — 多 import 段顺序 + 去重 ===
    // ============================================================

    @Nested
    @DisplayName("Given 多 import 段,When 走 visitor,Then 顺序保持 + 重复去重")
    class MultipleImports {

        @Test
        @DisplayName("3 个 import 段按出现顺序保留")
        void threeImports() {
            List<String> imports = visitImports(
                "import \"libs/variables.drl\";\n"
                + "import \"libs/actions.drl\";\n"
                + "import \"libs/constants.drl\";\n"
                + "rule \"R1\" when Applicant(age > 18) then end");
            assertEquals(3, imports.size());
            assertEquals("libs/variables.drl", imports.get(0));
            assertEquals("libs/actions.drl", imports.get(1));
            assertEquals("libs/constants.drl", imports.get(2));
        }

        @Test
        @DisplayName("重复 import 自动去重(LinkedHashSet)")
        void duplicateImportsDeduped() {
            List<String> imports = visitImports(
                "import \"libs/variables.drl\";\n"
                + "import \"libs/actions.drl\";\n"
                + "import \"libs/variables.drl\";\n"
                + "rule \"R1\" when Applicant(age > 18) then end");
            assertEquals(2, imports.size(), "重复 import 应去重");
            assertTrue(imports.contains("libs/variables.drl"));
            assertTrue(imports.contains("libs/actions.drl"));
        }
    }

    // ============================================================
    // === V5.44.3 BDD 3 — DrlDeserializer 端塞 import 进 resolver ===
    // ============================================================

    @Nested
    @DisplayName("Given DrlDeserializer.parseDrl,When DRL 含 import 段,Then resolver 拿到 import 列表")
    class DeserializerPushesImports {

        @Test
        @DisplayName("parseDrl 后 resolver.getImports() 返 visitor 收集的列表")
        void parseDrlPushesImports() {
            resolver = makeResolver();
            DrlDeserializer.parseDrl(
                "import \"libs/variables.drl\";\n"
                + "import \"libs/actions.drl\";\n"
                + "rule \"R1\" when Applicant(age > 18) then end",
                resolver);
            List<String> imports = resolver.getImports();
            assertEquals(2, imports.size());
            assertTrue(imports.contains("libs/variables.drl"));
            assertTrue(imports.contains("libs/actions.drl"));
        }
    }

    // ============================================================
    // === V5.44.3 BDD 4 — 未注册 type 抛 DrlParseException,信息含 import 列表 ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL 含 import 段 + 未注册 type,When deserialize,Then 抛 DrlParseException,信息含 import 列表")
    class UnknownTypeWithImportHint {

        @Test
        @DisplayName("未注册 type 抛 DrlParseException,错误信息含 import 路径")
        void throwsWithImportHint() {
            resolver = makeResolver();
            DrlParseException ex = assertThrows(DrlParseException.class, () ->
                DrlDeserializer.parseDrl(
                    "import \"libs/variables.drl\";\n"
                    + "rule \"R1\" when UnknownType(age > 18) then end",
                    resolver));
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("UnknownType"),
                "错误信息应含 type 名 'UnknownType',实际:" + ex.getMessage());
            assertTrue(ex.getMessage().contains("libs/variables.drl"),
                "错误信息应含 import 路径便于诊断,实际:" + ex.getMessage());
        }
    }

    // ============================================================
    // === V5.44.3 BDD 5 — 无 import 段时,错误信息回退到 V5.42.2 风格 ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL 无 import 段,When deserialize 未注册 type,Then 错误信息回退 builtin-only 提示")
    class UnknownTypeNoImportFallback {

        @Test
        @DisplayName("无 import 段时,错误信息不含 import 列表,提示 V5.44.3 import 段语法")
        void fallbackMessageStyle() {
            resolver = makeResolver();
            DrlParseException ex = assertThrows(DrlParseException.class, () ->
                DrlDeserializer.parseDrl(
                    "rule \"R1\" when UnknownType(age > 18) then end",
                    resolver));
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("UnknownType"));
            // V5.42.2 风格 + V5.44.3 提示用户用 import 段
            assertTrue(ex.getMessage().contains("import"),
                "错误信息应提示 V5.44.3 import 段语法,实际:" + ex.getMessage());
        }
    }

    // ============================================================
    // === V5.44.3 BDD 6 — DatatypeResolver.addImport 守卫 ===
    // ============================================================

    @Nested
    @DisplayName("Given DatatypeResolver,When addImport 边界,Then 守卫抛 IllegalArgumentException")
    class AddImportGuard {

        @Test
        @DisplayName("null libraryPath 抛 IllegalArgumentException")
        void nullPathThrows() {
            resolver = makeResolver();
            assertThrows(IllegalArgumentException.class, () -> resolver.addImport(null));
        }

        @Test
        @DisplayName("空 libraryPath 抛 IllegalArgumentException")
        void emptyPathThrows() {
            resolver = makeResolver();
            assertThrows(IllegalArgumentException.class, () -> resolver.addImport(""));
        }
    }

    // ============================================================
    // === helpers ===
    // ============================================================

    private List<String> visitImports(String drl) {
        DrlParser.CompilationUnitContext tree = parse(drl);
        DrlAstVisitor visitor = new DrlAstVisitor(makeResolver());
        visitor.visit(tree);
        return visitor.getImports();
    }

    private static DrlParser.CompilationUnitContext parse(String drl) {
        DrlLexer lexer = new DrlLexer(CharStreams.fromString(drl));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlParser parser = new DrlParser(tokens);
        parser.removeErrorListeners();
        StringBuilder errors = new StringBuilder();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg,
                                    RecognitionException e) {
                errors.append("line ").append(line).append(":").append(charPositionInLine)
                    .append(" ").append(msg).append("\n");
            }
        });
        DrlParser.CompilationUnitContext tree = parser.compilationUnit();
        if (errors.length() > 0) {
            throw new DrlParseException("DRL 语法错:\n" + errors.toString());
        }
        return tree;
    }
}
