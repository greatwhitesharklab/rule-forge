package com.ruleforge.ir.drl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.45.1 — DRL 4 {@code declare} 段 schema 完整化 BDD。
 *
 * <p>锁 6 件事:
 * <ol>
 *   <li>单 declare 段 + 1 字段 → visitor 抽 1 个 TypeInfo,字段列表正确</li>
 *   <li>declare 段含 {@code extends} → TypeInfo.extendsName 字段被填</li>
 *   <li>{@code @role(event)} 形式 annotation → TypeInfo.annotations 收集</li>
 *   <li>{@code @timestamp("created")} 形式 annotation(String 参数)→ annotations 记录</li>
 *   <li>嵌套 declare(父 declare 段内含子 declare 段)→ 父子两个 TypeInfo 都收集</li>
 *   <li>多 annotation 同一 declare 段 → annotations 列表含多 entry</li>
 * </ol>
 *
 * <p>V5.45.1 边界:
 * <ul>
 *   <li>annotation 段出现位置 — 顶层 declareStatement 头(在 {@code declare} 关键字前)
 *       或 fields 之间;grammar 接受这两种位置</li>
 *   <li>annotation 参数 — V5.45.1 覆盖 {@code IDENTIFIER (LPAREN annotationArgs RPAREN)?} —
 *       0 参数 / IDENTIFIER / STRING / {@code key=STRING} 三种参数形式</li>
 *   <li>annotation 不带括号也算合法(Drools 约定),V5.45.1 接受</li>
 * </ul>
 *
 * <p>本测试**不**测 TypeInfo 完整字段(annotation 值 / 字段 label 留给 V5.46+)—
 * 只锁"grammar 接受 + visitor 收集到 declaredTypes 集合"两条最核心契约。
 */
@DisplayName("V5.45.1 — DRL declare 段 schema 完整化 BDD")
class DrlDeclareGrammarTest {

    /**
     * V5.45.1 — parse 一段 DRL 文本,跑 visitor,拿 declaredTypes。
     * 跟 V5.42.2 DrlAstVisitor 一致路径,仅加 declare 段支持。
     */
    private Map<String, DatatypeResolver.TypeInfo> parseDeclaredTypes(String drl) {
        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(drl);
        com.ruleforge.drl.DrlLexer lexer = new com.ruleforge.drl.DrlLexer(input);
        org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);
        com.ruleforge.drl.DrlParser parser = new com.ruleforge.drl.DrlParser(tokens);
        parser.removeErrorListeners();
        com.ruleforge.drl.DrlParser.CompilationUnitContext tree = parser.compilationUnit();
        DatatypeResolver resolver = new DatatypeResolver();
        DrlAstVisitor visitor = new DrlAstVisitor(resolver);
        visitor.visit(tree);
        return visitor.getDeclaredTypes();
    }

    // ============================================================
    // === V5.45.1 BDD 1 — 单 declare 段 + 1 字段 ===
    // ============================================================

    @Nested
    @DisplayName("Given 单 declare 段 + 1 字段,When parse,Then 抽出 1 个 TypeInfo")
    class SingleDeclare {

        @Test
        @DisplayName("最简 declare Applicant age:int → 1 TypeInfo name=Applicant fields=[age]")
        void singleField() {
            String drl = "declare Applicant\n    age : int\nend";
            Map<String, DatatypeResolver.TypeInfo> types = parseDeclaredTypes(drl);
            assertEquals(1, types.size());
            DatatypeResolver.TypeInfo info = types.get("Applicant");
            assertNotNull(info);
            assertEquals("Applicant", info.getName());
            assertEquals(java.util.List.of("age"), info.getFields());
        }

        @Test
        @DisplayName("多字段 declare Person name:String age:int income:double → 3 字段按声明顺序")
        void multipleFields() {
            String drl =
                "declare Person\n"
                + "    name : String\n"
                + "    age : int\n"
                + "    income : double\n"
                + "end";
            Map<String, DatatypeResolver.TypeInfo> types = parseDeclaredTypes(drl);
            assertEquals(1, types.size());
            DatatypeResolver.TypeInfo info = types.get("Person");
            assertEquals(java.util.List.of("name", "age", "income"), info.getFields());
        }
    }

    // ============================================================
    // === V5.45.1 BDD 2 — extends 段 ===
    // ============================================================

    @Nested
    @DisplayName("Given declare 段含 extends,When parse,Then TypeInfo.extendsName 被填")
    class ExtendsDecl {

        @Test
        @DisplayName("declare Student extends Person → extendsName=Person")
        void extendsClause() {
            String drl =
                "declare Student\n"
                + "    extends Person\n"
                + "    gpa : double\n"
                + "end";
            Map<String, DatatypeResolver.TypeInfo> types = parseDeclaredTypes(drl);
            assertEquals(1, types.size());
            DatatypeResolver.TypeInfo info = types.get("Student");
            assertEquals("Person", info.getExtendsName());
            assertEquals(java.util.List.of("gpa"), info.getFields());
        }

        @Test
        @DisplayName("无 extends 段 → extendsName=null(默认)")
        void noExtends() {
            String drl = "declare Applicant\n    age : int\nend";
            Map<String, DatatypeResolver.TypeInfo> types = parseDeclaredTypes(drl);
            DatatypeResolver.TypeInfo info = types.get("Applicant");
            assertNull(info.getExtendsName());
        }
    }

    // ============================================================
    // === V5.45.1 BDD 3 — annotation @role(event) 形式 ===
    // ============================================================

    @Nested
    @DisplayName("Given declare 段含 annotation,When parse,Then TypeInfo.annotations 收集")
    class Annotations {

        @Test
        @DisplayName("@role(event) annotation 收集到 TypeInfo.annotations")
        void roleEvent() {
            String drl =
                "@role(event)\n"
                + "declare Applicant\n"
                + "    age : int\n"
                + "end";
            Map<String, DatatypeResolver.TypeInfo> types = parseDeclaredTypes(drl);
            DatatypeResolver.TypeInfo info = types.get("Applicant");
            assertNotNull(info);
            assertTrue(info.getAnnotations().containsKey("role"));
            assertEquals("event", info.getAnnotations().get("role"));
        }

        @Test
        @DisplayName("@timestamp(\"created\") annotation(STRING 参数)收集到 annotations")
        void timestampArg() {
            String drl =
                "@role(event)\n"
                + "@timestamp(\"created\")\n"
                + "declare Applicant\n"
                + "    age : int\n"
                + "end";
            Map<String, DatatypeResolver.TypeInfo> types = parseDeclaredTypes(drl);
            DatatypeResolver.TypeInfo info = types.get("Applicant");
            // V5.45.1 简化:annotation 整体形参为字符串形式(原 annotation "event"
            // 整体串起来记到 map);复杂 parse 留 V5.46+
            assertNotNull(info);
            assertTrue(info.getAnnotations().containsKey("role"));
            assertTrue(info.getAnnotations().containsKey("timestamp"));
        }

        @Test
        @DisplayName("多 annotation 同一 declare 段 → annotations 含多 entry")
        void multipleAnnotations() {
            String drl =
                "@role(event)\n"
                + "@timestamp(\"created\")\n"
                + "@expires(\"1h\")\n"
                + "declare Applicant\n"
                + "    age : int\n"
                + "end";
            Map<String, DatatypeResolver.TypeInfo> types = parseDeclaredTypes(drl);
            DatatypeResolver.TypeInfo info = types.get("Applicant");
            assertEquals(3, info.getAnnotations().size());
        }
    }

    // ============================================================
    // === V5.45.1 BDD 4 — 嵌套 declare ===
    // ============================================================

    @Nested
    @DisplayName("Given 嵌套 declare(父 declare 段内含子 declare 段),When parse,Then 父子都收集")
    class NestedDeclare {

        @Test
        @DisplayName("declare Company 内部 declare Address → 2 TypeInfo 都被收集")
        void nested() {
            String drl =
                "declare Company\n"
                + "    name : String\n"
                + "    declare Address\n"
                + "        city : String\n"
                + "        zip : String\n"
                + "    end\n"
                + "end";
            Map<String, DatatypeResolver.TypeInfo> types = parseDeclaredTypes(drl);
            assertEquals(2, types.size());
            assertNotNull(types.get("Company"));
            assertNotNull(types.get("Address"));
            assertEquals(java.util.List.of("city", "zip"), types.get("Address").getFields());
        }
    }
}
