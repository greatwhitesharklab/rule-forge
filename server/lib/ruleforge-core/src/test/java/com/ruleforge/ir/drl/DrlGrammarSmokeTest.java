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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * V5.42.1 — DRL 4 grammar(自研 ANTLR4,Apache 2.0 clean)smoke test。
 *
 * <p>纯 grammar 层验证:lexer 切 token 流 + parser 切 ParseTree。
 * <b>不</b>依赖 DrlAstVisitor(V5.42.2 才实现),<b>不</b>依赖 Rule model
 * (V5.42.4 才接)。本测试只证明 grammar 写对了 — 用户能写标准 DRL 4 语法,
 * RuleForge parser 就能 parse 出 ParseTree。
 *
 * <p>覆盖范围(从 V5.42 plan 锁定):
 * <ul>
 *   <li>正 corpus: package / dialect / rule(基础 + 11 个 attribute + extends(D2))/
 *       when(lhs 6 种)/ then(rhs 3 种)/ end / 表达式 13 种 op / 占位符 ${...} /
 *       accumulate 5 内置(count/sum/avg/min/max)init/action/result 3 段(D3 reverse 砍掉)</li>
 *   <li>负 corpus: import / accumulate reverse 段 / 'function' / 'declare' —
 *       grammar 不支持 → 报 syntax error</li>
 * </ul>
 *
 * @since 5.42
 */
@DisplayName("V5.42.1 — DRL 4 grammar(ANTLR4)smoke test")
class DrlGrammarSmokeTest {

    // ============================================================
    // === Positive corpus:RuleForge 支持的 DRL 4 子集 ===
    // ============================================================

    @Nested
    @DisplayName("Given 合法 DRL 4 片段,When 解析,Then 拿到 ParseTree")
    class Positive {

        @Test
        @DisplayName("最简单的 rule(name + when + then + end)")
        void simplestRule() {
            String drl = "rule \"R1\" when $a : Applicant(age > 18) then $a.setApproved(true); end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("package 顶层语句")
        void withPackage() {
            String drl = "package com.ruleforge.rules\n" +
                         "rule \"R1\" when $a : Applicant(age > 18) then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("dialect 顶层语句(mvel/java)")
        void withDialect() {
            String drl = "package com.ruleforge\n" +
                         "dialect \"mvel\"\n" +
                         "rule \"R1\" when $a : Applicant() then end";
            assertParses(drl, 2, 1);
        }

        @Test
        @DisplayName("D2 决定:rule X extends Y")
        void ruleExtends() {
            String drl = "rule \"R1\" extends \"R0\" when $a : Applicant(age > 18) then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("11 个 attribute 全到(salience/agenda-group/.../timer)")
        void allAttributes() {
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
                "timer (cron(\"0 0 12 * * ?\"))] " +
                "when $a : Applicant() then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("timer int(秒)形式")
        void timerInt() {
            String drl = "rule \"R1\" [timer (int(60))] when $a : Applicant() then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("lhs:not 模式")
        void lhsNot() {
            String drl = "rule \"R1\" when not Applicant(banned == true) then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("lhs:exists 模式")
        void lhsExists() {
            String drl = "rule \"R1\" when exists Applicant(age > 18) then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("lhs:eval 形式")
        void lhsEval() {
            String drl = "rule \"R1\" when eval(1 + 1 == 2) then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("lhs:from 形式 — V5.50.1 收口")
        void lhsFrom() {
            String drl = "rule \"R1\" when $a : Applicant(age > 18) from $stream then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("lhs:collect 形式 — V5.50.1 收口")
        void lhsCollect() {
            String drl = "rule \"R1\" when $xs : ArrayList() from collect(Applicant(age > 18)) then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("D3 决定:accumulate 5 内置 count + init/action/result 3 段,无 reverse — V5.50.1 收口")
        void lhsAccumulateCount() {
            String drl = "rule \"R1\" " +
                "when $n : Number() from accumulate(Applicant(age > 18), " +
                "init(count := 0), " +
                "action($n.setValue(count + 1)), " +
                "result(count)) " +
                "then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("accumulate 5 内置 sum — V5.50.3 收口")
        void lhsAccumulateSum() {
            String drl = "rule \"R1\" " +
                "when $s : Integer() from accumulate(Loan(amount > 1000), " +
                "init(int total := 0), " +
                "action($s.setValue(total + $loan.getAmount())), " +
                "result(total)) " +
                "then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("表达式 13 种 op 合并 pattern — V5.50.2 收口")
        void allOperators() {
            String drl = "rule \"R1\" when " +
                "$a : Applicant(age > 18 && age <= 65, " +
                "income >= 10000, " +
                "score != 0, " +
                "status in (\"active\", \"pending\"), " +
                "status not in (\"banned\"), " +
                "name matches \"John.*\", " +
                "tags contains \"vip\", " +
                "city memberOf $allowedCities, " +
                "remark soundslike \"hello\") " +
                "then $a.setScore($a.getScore() + 10 * 2 - 5 / 3 % 2); end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("占位符 ${...} 透传")
        void placeholderPassthrough() {
            String drl = "rule \"R1\" when Applicant(age > ${minAge}) then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("rhs 三种 statement:assign / methodCall / expr — V5.50.1 收口")
        void rhsStatements() {
            String drl = "rule \"R1\" " +
                "when $a : Applicant(age > 18) " +
                "then " +
                "$a.setScore(100); " +
                "$a.setApproved(true); " +
                "end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("query 基础子集 — V5.50.3 收口")
        void queryBasic() {
            String drl = "package com.ruleforge\n" +
                "query \"Q1\"(Integer $min) $a : Applicant(age > $min) end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("function 基础子集 — V5.50.3 收口")
        void functionBasic() {
            String drl = "package com.ruleforge\n" +
                "function Integer myFn(Integer x) { return x + 1; }";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("declare 基础子集 — V5.50.4 收口")
        void declareBasic() {
            String drl = "package com.ruleforge\n" +
                "declare Applicant extends Person name : String age : Integer end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("declare 多 primitive types:long / double / float / short / byte / char / boolean — V5.50.4 收口")
        void declarePrimitiveTypes() {
            // V5.45.1 fieldType 已扩 UPPER_IDENTIFIER + IDENTIFIER + DRL_TIMER_INT + DRL_TIMER_CRON,
            // primitive 关键字(long / double / float / short / byte / char / boolean)走 IDENTIFIER alt。
            // 本测试锁 V5.50.4 不回退。
            String drl = "package com.ruleforge\n" +
                "declare Person\n" +
                "  age : int\n" +
                "  salary : long\n" +
                "  weight : double\n" +
                "  height : float\n" +
                "  yearBorn : short\n" +
                "  flag : byte\n" +
                "  initial : char\n" +
                "  active : boolean\n" +
                "end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("多 rule 编译单元")
        void multipleRules() {
            String drl = "package com.ruleforge\n" +
                "rule \"R1\" when Applicant(age > 18) then end\n" +
                "rule \"R2\" when Applicant(income > 5000) then end";
            assertParses(drl, 1, 2);
        }

        @Test
        @DisplayName("string method:starts-with / ends-with / length — V5.50.1 收口")
        void stringMethods() {
            String drl = "rule \"R1\" when " +
                "$a : Applicant(name[starts-with \"Mr\"]) " +
                "then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("注释 + 空行 + 缩进")
        void whitespaceAndComments() {
            String drl = "package com.ruleforge // 顶层包\n" +
                "\n" +
                "// 单 rule\n" +
                "rule \"R1\"\n" +
                "    when $a : Applicant(age > 18) /* inline comment */ then\n" +
                "        $a.setApproved(true);\n" +
                "    end";
            assertParses(drl, 1, 1);
        }
    }

    // ============================================================
    // === Negative corpus:V5.42 plan 显式不支持的 DRL 4 特性 ===
    // ============================================================

    @Nested
    @DisplayName("Given V5.42 不支持的 DRL 4 特性,When 解析,Then 报 syntax error")
    class Negative {

        @Test
        @DisplayName("import 段(lexer 缺失 → token error)")
        void rejectsImport() {
            String drl = "package com.ruleforge\n" +
                "import com.ruleforge.model.Applicant\n" +
                "rule \"R1\" when Applicant() then end";
            assertParseFails(drl);
        }

        @Test
        @DisplayName("D3 决定:accumulate reverse 段(grammar rule 缺失 → syntax error)")
        void rejectsAccumulateReverse() {
            String drl = "rule \"R1\" " +
                "when $s : Integer() from accumulate(Loan(amount > 100), " +
                "init(int t = 0), " +
                "action($s.setValue(t + $loan.getAmount())), " +
                "reverse($s.setValue(t - $loan.getAmount())), " +
                "result(t)) " +
                "then end";
            assertParseFails(drl);
        }

        @Test
        @DisplayName("非法顶层关键字(notDrools)")
        void rejectsUnknownKeyword() {
            String drl = "rule \"R1\" when notDrools(Applicant()) then end";
            assertParseFails(drl);
        }
    }

    // ============================================================
    // === V5.50.1 P0 DRL grammar 收口 — BDD scaffold ===
    // ============================================================
    //
    // 10 个 @Disabled DRL grammar 边缘中,V5.50.1 收 5 个 P0(lhsFrom / lhsCollect /
    // rhsStatements / lhsAccumulateCount / stringMethods)。本 @Nested 锁 5 段 DRL
    // 文本作为 grammar 期望,V5.50.1 commit 改完 DrlParser.g4 后这 5 个 @Test 全绿。
    //
    // 跟 Positive nested class 内 5 个 @Disabled 的区别:Positive 的 5 个 @Disabled
    // 测的是同一段 DRL,但 V5.50.1 commit 删 @Disabled 时一并 unskip,本 nested 是
    // 独立锁 — 防止后续 commit 不小心改 grammar 把这 5 段回退成"碰巧通过"。
    //
    // V5.50.1 风险:本 nested 在 V5.50.1 grammar 改完前全红(red),改完后全绿
    // (green) — 这是 BDD 标准 TDD 循环,不视作 bug。

    @Nested
    @DisplayName("V5.50.1 P0 — 5 个 DRL grammar 边缘 grammar 层 lock-in")
    class V5_50_1_P0_GrammarScaffold {

        @Test
        @DisplayName("Given DRL lhs:from 形式(Applicant from $stream),When 解析,Then 无 syntax error")
        void lhsFromGrammarLockIn() {
            String drl = "rule \"R1\" when $a : Applicant(age > 18) from $stream then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("Given DRL lhs:collect 形式(ArrayList from collect(...)),When 解析,Then 无 syntax error")
        void lhsCollectGrammarLockIn() {
            String drl = "rule \"R1\" when $xs : ArrayList() from collect(Applicant(age > 18)) then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("Given DRL rhs 多种 statement($a.setScore + $a.setApproved),When 解析,Then 无 syntax error")
        void rhsStatementsGrammarLockIn() {
            String drl = "rule \"R1\" " +
                "when $a : Applicant(age > 18) " +
                "then " +
                "$a.setScore(100); " +
                "$a.setApproved(true); " +
                "end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("Given DRL accumulate count 3 段(init/action/result),When 解析,Then 无 syntax error")
        void lhsAccumulateCountGrammarLockIn() {
            String drl = "rule \"R1\" " +
                "when $n : Number() from accumulate(Applicant(age > 18), " +
                "init(count := 0), " +
                "action($n.setValue(count + 1)), " +
                "result(count)) " +
                "then end";
            assertParses(drl, 1, 1);
        }

        @Test
        @DisplayName("Given DRL string method(name[starts-with \"Mr\"]),When 解析,Then 无 syntax error")
        void stringMethodsGrammarLockIn() {
            String drl = "rule \"R1\" when " +
                "$a : Applicant(name[starts-with \"Mr\"]) " +
                "then end";
            assertParses(drl, 1, 1);
        }
    }

    // ============================================================
    // === V5.50.1 不变量 lock-in:accumulate reverse 段继续被拒绝(D3 决定) ===
    // ============================================================
    //
    // V5.42.1 plan D3 决定:accumulate reverse 段 grammar 砍掉,reverse 段继续被拒绝。
    // V5.50.1 改 accumulateInit 时,本 nested 锁这个不变量 — 反向测试,确保改 grammar
    // 时没"顺手"加 reverse alt。

    @Nested
    @DisplayName("V5.50.1 不变量 — accumulate reverse 段继续被拒绝(D3 决定不变)")
    class V5_50_1_AssertsAccumulateReverseStillRejected {

        @Test
        @DisplayName("Given DRL accumulate 含 reverse($s.setValue(t - $loan.getAmount())),When 解析,Then 报 syntax error")
        void rejectsAccumulateReverse() {
            String drl = "rule \"R1\" " +
                "when $s : Integer() from accumulate(Loan(amount > 100), " +
                "init(int t = 0), " +
                "action($s.setValue(t + $loan.getAmount())), " +
                "reverse($s.setValue(t - $loan.getAmount())), " +
                "result(t)) " +
                "then end";
            assertParseFails(drl);
        }
    }

    // ============================================================
    // === V5.51.2 migration 收口:PENDING_LHS 字段已删,caller 切到 Rule.lhs.criterion 链 ===
    // ============================================================
    //
    // V5.50.1 的 PENDING_LHS 字段 + getPendingLhsCriteria 方法已删(V5.51.2
    // migration 收口)。PropertyCriteria 全部走 extractLhs → toCriteria →
    // And.addCriterion → Lhs.setCriterion 链。caller 读 rule.getLhs().getCriterion()
    // 链拿到 And → Criteria。本 nested 锁:
    //   - PENDING_LHS 字段 0 引用(反射 NoSuchFieldException = 成功路径)
    //   - getPendingLhsCriteria 方法 0 引用(反射 NoSuchMethodException = 成功路径)

    @Nested
    @DisplayName("V5.51.2 migration — PENDING_LHS 字段 + 方法已删(合同兑现)")
    class V5_50_1_PendingLhsMigration {

        @Test
        @DisplayName("DrlDeserializer.PENDING_LHS 字段必须不存在(V5.51.2 删字段)")
        void pendingLhsFieldShouldBeGone() {
            // Given/When — 反射读 DrlDeserializer.PENDING_LHS
            // Then — 抛 NoSuchFieldException 是**成功**路径(V5.51.2 已删字段)
            try {
                Class<?> cls = Class.forName("com.ruleforge.ir.drl.DrlDeserializer");
                cls.getDeclaredField("PENDING_LHS");
                fail("DrlDeserializer.PENDING_LHS 字段不应该存在 — V5.51.2 必须删字段 + 切 caller");
            } catch (NoSuchFieldException e) {
                // success path
            } catch (ClassNotFoundException e) {
                fail("DrlDeserializer 类找不到: " + e);
            }
        }

        @Test
        @DisplayName("DrlDeserializer.getPendingLhsCriteria(Rule) 方法必须不存在(V5.51.2 删方法)")
        void getPendingLhsCriteriaMethodShouldBeGone() {
            // Given/When — 反射读 DrlDeserializer.getPendingLhsCriteria
            // Then — 抛 NoSuchMethodException 是**成功**路径(V5.51.2 已删方法)
            try {
                Class<?> cls = Class.forName("com.ruleforge.ir.drl.DrlDeserializer");
                cls.getDeclaredMethod("getPendingLhsCriteria", com.ruleforge.model.rule.Rule.class);
                fail("DrlDeserializer.getPendingLhsCriteria 方法不应该存在 — V5.51.2 必须删方法 + 切 caller");
            } catch (NoSuchMethodException e) {
                // success path
            } catch (ClassNotFoundException e) {
                fail("DrlDeserializer 类找不到: " + e);
            }
        }
    }

    // ============================================================
    // === Helpers ===
    // ============================================================

    /**
     * 跑 lexer + parser,期待无 syntax error。
     *
     * @param drl  DRL 文本
     * @param expectedPackages 期望 packageStatement 数(0 或 1)
     * @param expectedRules    期望 unitStatement 里的 rule 数
     */
            private void assertParses(String drl, int expectedPackages, int expectedRules) {
        List<String> errors = new ArrayList<>();
        DrlParser parser = parse(drl, errors);
        assertNotNull(parser, "Parser 没初始化");
        assertTrue(errors.isEmpty(),
            "Expected no syntax errors, but got: " + errors);
    }

    /** 跑 lexer + parser,期待至少一个 syntax error。 */
    private void assertParseFails(String drl) {
        List<String> errors = new ArrayList<>();
        DrlParser parser = parse(drl, errors);
        assertNotNull(parser, "Parser 没初始化");
        assertTrue(!errors.isEmpty(),
            "Expected at least one syntax error, but parse succeeded for:\n" + drl);
    }

    /** 通用 parse:收集 syntax errors 到 list。 */
    private DrlParser parse(String drl, List<String> errors) {
        DrlLexer lexer = new DrlLexer(CharStreams.fromString(drl));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DrlParser parser = new DrlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg,
                                    RecognitionException e) {
                errors.add("line " + line + ":" + charPositionInLine + " " + msg);
            }
        });
        try {
            parser.compilationUnit();
        } catch (Exception ex) {
            errors.add("exception: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        return parser;
    }
}
