package com.ruleforge.ir.drl;

import com.ruleforge.drl.DrlLexer;
import com.ruleforge.drl.DrlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.42.2 — DrlAstVisitor 走 ParseTree 产出 {@link ParsedDrlRule} 的 BDD。
 *
 * <p>7 BDD 分 3 组:
 * <ul>
 *   <li>顶层 rule metadata(name + 11 attribute + extends D2)— V5.42.2 主线</li>
 *   <li>DatatypeResolver 校验:V5.42 D4 决定 'import' 缺失,unknown type 抛 DrlParseException</li>
 *   <li>语法错(grammar 不支持)→ listener 抓 → 不进 visitor</li>
 * </ul>
 *
 * <p>visitor 不会做 Lhs/Rhs 反序列化(那是 V5.42.4 DrlDeserializer 工作)。
 *
 * @since 5.42
 */
@DisplayName("V5.42.2 — DrlAstVisitor + DatatypeResolver BDD")
class DrlAstVisitorTest {

    private DatatypeResolver resolver;

    @BeforeEach
    void setUp() {
        // V5.42.2 测试环境:预注册 2 个 fact type(模拟 console-ui 推送 type registry)
        resolver = new DatatypeResolver();
        resolver.register("Applicant",
            DatatypeResolver.TypeInfo.fact("Applicant",
                Arrays.asList("age", "income", "score", "name", "tags", "city", "remark", "status")));
        resolver.register("Loan",
            DatatypeResolver.TypeInfo.fact("Loan", Arrays.asList("amount")));
    }

    // ============================================================
    // === 顶层 rule metadata ===
    // ============================================================

    @Nested
    @DisplayName("Given 单 rule,When 走 visitor,Then 顶层 metadata 正确")
    class TopLevelMetadata {

        @Test
        @DisplayName("最简单 rule:只有 name + when + then + end")
        void simplest() {
            List<ParsedDrlRule> rules = visit(
                "rule \"R1\" when Applicant(age > 18) then end");
            assertEquals(1, rules.size());
            ParsedDrlRule r = rules.get(0);
            assertEquals("R1", r.getName());
            assertNull(r.getExtendsName());
            assertEquals(0, r.getAttributes().size());
            assertNotNull(r.getLhsParseTree(), "lhs ParseTree 应保留");
            assertNotNull(r.getRhsParseTree(), "rhs ParseTree 应保留");
        }

        @Test
        @DisplayName("D2 决定:rule 'X' extends 'Y'")
        void extendsRule() {
            List<ParsedDrlRule> rules = visit(
                "rule \"X_else\" extends \"X\" when Applicant(age > 18) then end");
            assertEquals(1, rules.size());
            assertEquals("X_else", rules.get(0).getName());
            assertEquals("X", rules.get(0).getExtendsName());
        }

        @Test
        @DisplayName("11 attribute 全解析:salience 10, agenda-group \"g\", 6 boolean, 2 date, timer")
        void all11Attributes() {
            String drl = "rule \"R1\" " +
                "[salience 10, " +
                "agenda-group \"g1\", " +
                "activation-group \"a1\", " +
                "ruleflow-group \"rf1\", " +
                "auto-focus true, " +
                "no-loop true, " +
                "lock-on-active true, " +
                "enabled true, " +
                "date-effective \"2026-01-01\", " +
                "date-expires \"2027-01-01\", " +
                "timer (int(60))] " +
                "when Applicant() then end";
            List<ParsedDrlRule> rules = visit(drl);
            assertEquals(1, rules.size());
            ParsedDrlRule r = rules.get(0);
            // 11 attribute 应该全部进入 attributes list(顶层 dialect visitor 解析后丢弃,不计)
            assertEquals(11, r.getAttributes().size());
            // spot-check 几个
            assertAttribute(r, "salience", "10");
            assertAttribute(r, "agenda-group", "g1");
            assertAttribute(r, "auto-focus", "true");
            assertAttribute(r, "date-effective", "2026-01-01");
            assertAttribute(r, "timer", "(int(60))");
        }

        @Test
        @DisplayName("顶层 dialect 解析后丢弃(不进入 attributes list)")
        void dialectDropped() {
            // V5.42 D4 决定:顶层 dialect("mvel"/"java")visitor 解析但不存 Rule.dialect
            // 用 attribute 形式 [dialect "mvel"] 测 — 也走相同路径丢弃
            String drl = "rule \"R1\" [dialect \"mvel\"] when Applicant() then end";
            List<ParsedDrlRule> rules = visit(drl);
            assertEquals(1, rules.size());
            // dialect 不应出现
            boolean hasDialect = rules.get(0).getAttributes().stream()
                .anyMatch(a -> "dialect".equals(a.getName()));
            assertTrue(!hasDialect,
                "顶层 dialect 解析后丢弃,不应在 attributes list;实际:"
                + rules.get(0).getAttributes());
        }

        @Test
        @DisplayName("多 rule 编译单元")
        void multipleRules() {
            String drl = "package com.ruleforge\n" +
                "rule \"R1\" when Applicant(age > 18) then end\n" +
                "rule \"R2\" when Loan(amount > 100) then end";
            List<ParsedDrlRule> rules = visit(drl);
            assertEquals(2, rules.size());
            assertEquals("R1", rules.get(0).getName());
            assertEquals("R2", rules.get(1).getName());
        }
    }

    // ============================================================
    // === DatatypeResolver 校验 ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL pattern 引用 type,When visitor 走,Then DatatypeResolver 校验")
    class DatatypeResolverCheck {

        @Test
        @DisplayName("已注册 type:Applicant → 通过")
        void knownTypePasses() {
            String drl = "rule \"R1\" when Applicant(age > 18) then end";
            // 不抛异常就是 pass
            List<ParsedDrlRule> rules = visit(drl);
            assertEquals(1, rules.size());
        }

        @Test
        @DisplayName("V5.42 D4:未注册 type 'Unknown' → 抛 DrlParseException,指明 D4 决定")
        void unknownTypeFails() {
            String drl = "rule \"R1\" when Unknown(age > 18) then end";
            DrlParseException ex = assertThrows(DrlParseException.class,
                () -> visit(drl));
            // 错误信息应该点名 V5.42 D4 + 引导用户用 declare
            assertTrue(ex.getMessage().contains("Unknown"),
                "异常信息应该包含未知 type 名,实际:" + ex.getMessage());
            assertTrue(ex.getMessage().contains("D4") || ex.getMessage().contains("import"),
                "异常信息应该提 D4 决定 / import 不支持,实际:" + ex.getMessage());
            assertTrue(ex.getMessage().contains("declare")
                    || ex.getMessage().contains("register"),
                "异常信息应该给出解法(用 declare 段 / 预 register),实际:" + ex.getMessage());
        }

        @Test
        @DisplayName("同 DRL 两个 type 都未注册:抛错时点第一个未注册 type")
        void multipleUnknownFailsAtFirst() {
            // 第 1 个 Unknown 在 setUp 没注册 — 立即抛错
            String drl = "rule \"R1\" when Unknown1() and Unknown2() then end";
            assertThrows(DrlParseException.class, () -> visit(drl));
        }

        @Test
        @DisplayName("动态 register 后,新 type 可用(console-ui 推送场景模拟)")
        void dynamicRegister() {
            String drl = "rule \"R1\" when Person(age > 18) then end";
            // Person 还没注册 — fail
            assertThrows(DrlParseException.class, () -> visit(drl));
            // 动态 register — 模拟 console-ui 推送 type registry
            resolver.register("Person",
                DatatypeResolver.TypeInfo.fact("Person", Arrays.asList("age")));
            // 现在能通过
            List<ParsedDrlRule> rules = visit(drl);
            assertEquals(1, rules.size());
        }
    }

    // ============================================================
    // === 语法错(grammar 不支持)→ visitor 不该跑 ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL 语法错,When visitor 跑,Then 走 ANTLR error listener 不进 visitor")
    class SyntaxError {

        @Test
        @DisplayName("import 段(grammar 不支持)→ ANTLR 报 syntax error")
        void importFailsAtParse() {
            String drl = "package com.ruleforge\n" +
                "import com.ruleforge.model.Applicant\n" +
                "rule \"R1\" when Applicant() then end";
            // 直接走 parse → 期望至少 1 个 syntax error
            int errorCount = parseAndCountErrors(drl);
            assertTrue(errorCount > 0,
                "import 段应该报 syntax error,实际 " + errorCount + " 个 error");
        }
    }

    // ============================================================
    // === Helpers ===
    // ============================================================

    private List<ParsedDrlRule> visit(String drl) {
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
            throw new DrlParseException(
                "DRL 语法错(grammar 不支持):\n" + errors.toString());
        }
        DrlAstVisitor visitor = new DrlAstVisitor(resolver);
        visitor.visit(tree);
        return visitor.getRules();
    }

    private int parseAndCountErrors(String drl) {
        DrlLexer lexer = new DrlLexer(CharStreams.fromString(drl));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlParser parser = new DrlParser(tokens);
        parser.removeErrorListeners();
        int[] count = {0};
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg,
                                    RecognitionException e) {
                count[0]++;
            }
        });
        parser.compilationUnit();
        return count[0];
    }

    private static void assertAttribute(ParsedDrlRule rule, String name, String expectedValue) {
        boolean found = rule.getAttributes().stream()
            .anyMatch(a -> name.equals(a.getName()) && expectedValue.equals(a.getValue()));
        assertTrue(found,
            "Expected attribute [" + name + "=" + expectedValue + "], got " + rule.getAttributes());
    }
}
