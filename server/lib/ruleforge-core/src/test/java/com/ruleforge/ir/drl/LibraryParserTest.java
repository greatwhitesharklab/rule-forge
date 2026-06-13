package com.ruleforge.ir.drl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.45.2 — Library .drl 解析器 BDD。
 *
 * <p>Library 文件**只能**含 declare 段 + 顶层 import 段(纯 type 元数据);
 * 不允许 rule / query / function(那些是业务规则)。本测试锁 5 件事:
 * <ol>
 *   <li>单 declare 段 library → types map 含 1 entry</li>
 *   <li>多 declare 段 library → types map 含多 entry(按声明顺序)</li>
 *   <li>顶层 import 段收集到 innerImports(供 BFS 递归加载)</li>
 *   <li>含 rule 段抛 DrlParseException(library 严禁业务段)</li>
 *   <li>declare 段含 extends → TypeInfo.extendsName 被填</li>
 * </ol>
 *
 * <p>本测试**不**测 KnowledgeBuilder BFS 递归行为(留 LibraryLoadFlowTest / LibraryLoadCycleTest)。
 */
@DisplayName("V5.45.2 — Library .drl 解析器 BDD")
class LibraryParserTest {

    private final LibraryParser parser = new LibraryParser();

    // ============================================================
    // === BDD 1 — 单 declare 段 ===
    // ============================================================

    @Nested
    @DisplayName("Given 单 declare 段 library,When parseLibraryDrl,Then types 含 1 entry")
    class SingleDeclare {

        @Test
        @DisplayName("library 含 1 个 declare Applicant age:int → 1 TypeInfo")
        void single() {
            String drl = "declare Applicant\n    age : int\nend\n";
            LibraryParser.LibraryParseResult r = parser.parseLibraryDrl(drl);
            assertEquals(1, r.types().size());
            DatatypeResolver.TypeInfo info = r.types().get("Applicant");
            assertNotNull(info);
            assertEquals(java.util.List.of("age"), info.getFields());
        }

        @Test
        @DisplayName("library 0 declare 段 → types 空 map(合法,只是没 type)")
        void empty() {
            String drl = "";
            LibraryParser.LibraryParseResult r = parser.parseLibraryDrl(drl);
            assertEquals(0, r.types().size());
            assertEquals(0, r.innerImports().size());
        }
    }

    // ============================================================
    // === BDD 2 — 多 declare 段 ===
    // ============================================================

    @Nested
    @DisplayName("Given 多 declare 段 library,When parseLibraryDrl,Then types 含多 entry 按声明顺序")
    class MultipleDeclares {

        @Test
        @DisplayName("library 含 3 declare 段 → types map size = 3")
        void multiple() {
            String drl =
                "declare Applicant\n    age : int\nend\n"
                + "declare Person\n    name : String\nend\n"
                + "declare Company\n    taxId : String\nend\n";
            LibraryParser.LibraryParseResult r = parser.parseLibraryDrl(drl);
            assertEquals(3, r.types().size());
            assertNotNull(r.types().get("Applicant"));
            assertNotNull(r.types().get("Person"));
            assertNotNull(r.types().get("Company"));
        }
    }

    // ============================================================
    // === BDD 3 — 顶层 import 段 ===
    // ============================================================

    @Nested
    @DisplayName("Given library 含顶层 import 段,When parseLibraryDrl,Then innerImports 收集")
    class TopLevelImport {

        @Test
        @DisplayName("library 顶层 `import \"libs/base.drl\";` → innerImports 含 1 条")
        void singleImport() {
            String drl =
                "import \"libs/base.drl\";\n"
                + "declare Applicant\n    age : int\nend\n";
            LibraryParser.LibraryParseResult r = parser.parseLibraryDrl(drl);
            assertEquals(java.util.List.of("libs/base.drl"), r.innerImports());
            assertEquals(1, r.types().size());
        }
    }

    // ============================================================
    // === BDD 4 — library 含 rule 段抛错 ===
    // ============================================================

    @Nested
    @DisplayName("Given library 含 rule 段,When parseLibraryDrl,Then 抛 DrlParseException")
    class RejectRuleSegment {

        @Test
        @DisplayName("library 含 rule \"R1\" when ... then end → 抛 DrlParseException")
        void rejectRule() {
            String drl =
                "rule \"R1\"\n    when\n        $a : Applicant(age > 18)\n    then\n        $a.setApproved(true);\nend\n";
            DrlParseException ex = assertThrows(DrlParseException.class,
                () -> parser.parseLibraryDrl(drl));
            assertTrue(ex.getMessage().contains("rule 段"),
                "exception message should mention 'rule 段': " + ex.getMessage());
        }
    }

    // ============================================================
    // === BDD 5 — declare 含 extends ===
    // ============================================================

    @Nested
    @DisplayName("Given library declare 段含 extends,When parseLibraryDrl,Then extendsName 填")
    class ExtendsInLibrary {

        @Test
        @DisplayName("declare Student extends Person → extendsName=Person")
        void extendsInLibrary() {
            String drl = "declare Student\n    extends Person\n    gpa : double\nend\n";
            LibraryParser.LibraryParseResult r = parser.parseLibraryDrl(drl);
            DatatypeResolver.TypeInfo info = r.types().get("Student");
            assertNotNull(info);
            assertEquals("Person", info.getExtendsName());
        }
    }
}
