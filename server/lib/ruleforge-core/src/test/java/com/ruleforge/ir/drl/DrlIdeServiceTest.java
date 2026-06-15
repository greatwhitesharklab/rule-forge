package com.ruleforge.ir.drl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V5.78.1 — DRL IDE 共享 service (core 层) 单测。
 *
 * <p>背景:Phase 14 完整 DRL 编辑器(console-ui)。后端需要给 IDE
 * 端点(parse-with-errors / complete / hover)提供一致的语法层 +
 * semantic 层 API,避免在 console-app 里再次实现 ANTLR parse 细节。
 *
 * <p>{@link DrlIdeService} 设计要点:
 * <ul>
 *   <li>每请求 {@code new DrlLexer + new DrlParser},跟 V5.44.4
 *       CommonController.parseDrlSummary 行为一致,线程安全无共享状态</li>
 *   <li>解析错误用 {@link SyntaxError} (line/column/msg) 收集而非
 *       抛 DrlParseException — IDE 端点是"live editing",半成品 DRL
 *       是常态,不应 500</li>
 *   <li>解析失败时仍尝试从 partial tree 提取 imports + rule names
 *       (ANTLR error recovery 会产生部分 tree;lenient visitor 接受),
 *       跟 V5.44.4 lenient 行为对齐</li>
 * </ul>
 *
 * <p>注:不在 core 层写 lsp4j / Spring 依赖 — 本类只负责"DRL 文本 →
 * 结构化 IDE 视图"转换,transport (HTTP / WS / stdio) 由调用方
 * (console-app DrlIdeController) 决定。
 *
 * @since 5.78
 */
@DisplayName("V5.78.1 — DRL IDE 共享 service (parse + complete + hover)")
class DrlIdeServiceTest {

    // ============================================================
    // === parseWithErrors — 给 IDE live diagnostics 用的入口 ===
    // ============================================================

    @Nested
    @DisplayName("Given 完整合法 DRL,When parseWithErrors,Then 0 错误 + 拿到 imports/rules")
    class ParseClean {

        @Test
        @DisplayName("单 rule + 1 import,no syntax error")
        void simplestRule() {
            String drl = "import \"libs/variables.drl\";\n" +
                    "rule \"R1\" when $a : Applicant(age > 18) then $a.setApproved(true); end";
            DrlIdeService.IdeParseResult result = new DrlIdeService().parseWithErrors(drl);
            assertNotNull(result);
            assertTrue(result.getErrors().isEmpty(), "无错但返了: " + result.getErrors());
            assertEquals(1, result.getImports().size());
            assertEquals("libs/variables.drl", result.getImports().get(0));
            assertEquals(1, result.getRules().size());
            assertEquals("R1", result.getRules().get(0).getName());
        }

        @Test
        @DisplayName("空 DRL 文件 (合法,只 EOF)")
        void emptyFile() {
            DrlIdeService.IdeParseResult result = new DrlIdeService().parseWithErrors("");
            assertNotNull(result);
            assertTrue(result.getErrors().isEmpty());
            assertTrue(result.getImports().isEmpty());
            assertTrue(result.getRules().isEmpty());
        }
    }

    @Nested
    @DisplayName("Given 含语法错 DRL,When parseWithErrors,Then 返 errors 列表 + line/column > 0")
    class ParseWithErrors {

        @Test
        @DisplayName("rule 段少 end")
        void missingEnd() {
            // 'when' 段没 'then' 直接到 EOF
            String drl = "rule \"R1\" when $a : Applicant(age > 18)";
            DrlIdeService.IdeParseResult result = new DrlIdeService().parseWithErrors(drl);
            assertFalse(result.getErrors().isEmpty(), "应有错");
            SyntaxError first = result.getErrors().get(0);
            assertTrue(first.getLine() > 0, "line 编号 1-based: " + first.getLine());
            assertTrue(first.getColumn() >= 0, "column >= 0: " + first.getColumn());
            assertNotNull(first.getMessage());
            assertFalse(first.getMessage().isBlank());
        }

        @Test
        @DisplayName("rule attribute 用了未定义关键字(syntax error)")
        void unknownAttribute() {
            String drl = "rule \"R1\" [foobar 42] when $a : Applicant(age > 18) then end";
            DrlIdeService.IdeParseResult result = new DrlIdeService().parseWithErrors(drl);
            assertFalse(result.getErrors().isEmpty(),
                    "未定义 attribute 应报 syntax error,实际: " + result.getErrors());
        }

        @Test
        @DisplayName("解析失败仍尝试提取 partial rule names (lenient 模式)")
        void partialRecovery() {
            // 'Applicant' 是 UPPER_IDENTIFIER(类型),不补 'type(...)' 也算合法
            // 这里构造的错是 when 后直接 EOF — 第一 rule 不完整,但 ANTLR
            // error recovery 会接受后面的 rule
            String drl = "rule \"R1\" when\nrule \"R2\" when $a : Applicant(age > 18) then $a.setApproved(true); end";
            DrlIdeService.IdeParseResult result = new DrlIdeService().parseWithErrors(drl);
            // 至少应包含 R2 (R1 残缺 visitor lenient 也可能跳过)
            boolean foundR2 = result.getRules().stream()
                    .anyMatch(r -> "R2".equals(r.getName()));
            assertTrue(foundR2, "R2 应被 partial recovery 抓到,实际 rules: "
                    + result.getRules().stream().map(ParsedDrlRule::getName).toList());
        }
    }

    // ============================================================
    // === complete — keyword/builtin completion ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL 文本 + caret offset,When complete,Then 返关键字 + TypeInfo field 候选")
    class Complete {

        @Test
        @DisplayName("空文本 + caret 0,返所有 DRL top-level 关键字")
        void emptyTextAllKeywords() {
            List<DrlIdeService.Completion> completions =
                    new DrlIdeService().complete("", 0);
            assertFalse(completions.isEmpty());
            // 至少包含 rule / import / package / when / then / end
            List<String> labels = completions.stream()
                    .map(DrlIdeService.Completion::getLabel).toList();
            assertTrue(labels.contains("rule"), "缺 rule 关键字: " + labels);
            assertTrue(labels.contains("when"), "缺 when 关键字: " + labels);
            assertTrue(labels.contains("then"), "缺 then 关键字: " + labels);
            assertTrue(labels.contains("end"), "缺 end 关键字: " + labels);
        }

        @Test
        @DisplayName("declare X { age : Integer },complete 包含 'age' field")
        void declaredFieldAppears() {
            String drl = "declare Applicant age : Integer end\n";
            // caret 在 EOF 后;complete 应包含 Applicant 字段
            List<DrlIdeService.Completion> completions =
                    new DrlIdeService().complete(drl, drl.length());
            List<String> labels = completions.stream()
                    .map(DrlIdeService.Completion::getLabel).toList();
            assertTrue(labels.contains("age"),
                    "声明的 field 'age' 应出现在 complete: " + labels);
        }
    }

    // ============================================================
    // === hover — builtin 关键字 + TypeInfo field 提示 ===
    // ============================================================

    @Nested
    @DisplayName("Given DRL 文本 + (line,col),When hover,Then 返 markdown 说明 (or null)")
    class Hover {

        @Test
        @DisplayName("hover 'rule' 关键字返 builtin 说明")
        void hoverBuiltin() {
            // "rule" 出现在第 0 行
            DrlIdeService.HoverInfo info = new DrlIdeService()
                    .hover("rule \"R1\" when then end", 0, 1);
            assertNotNull(info, "'rule' 关键字应能 hover");
            assertTrue(info.getContents().toLowerCase().contains("rule")
                            || info.getContents().contains("规则"),
                    "hover 内容应提到 rule/规则: " + info.getContents());
        }

        @Test
        @DisplayName("hover 普通空白处返 null")
        void hoverWhitespace() {
            // 0,4 是 "rule" 后的空格,regex 抓不到 ident,hover 返 null
            DrlIdeService.HoverInfo info = new DrlIdeService()
                    .hover("rule \"R1\" when then end", 0, 4);
            // 返 null 是合理(空格/字符串/数字),断言不抛 + 不返非 null
            // (V5.78.1 实现:无 ident match → 返 null)
            org.junit.jupiter.api.Assertions.assertNull(info);
        }

        @Test
        @DisplayName("hover declare 的 field 返 TypeInfo field 说明")
        void hoverDeclaredField() {
            String drl = "declare Applicant age : Integer end\n" +
                    "rule \"R1\" when Applicant(age > 18) then end\n";
            // 'age' 在 line 1 第二个 'age' 出现位置:Applicant(age 开头
            int colOfAge = drl.indexOf("age > 18");
            int lineOfAge = drl.substring(0, colOfAge).split("\n").length - 1;
            DrlIdeService.HoverInfo info = new DrlIdeService()
                    .hover(drl, lineOfAge, colOfAge - drl.lastIndexOf("\n", colOfAge) - 1);
            assertNotNull(info, "声明的 field 'age' 应能 hover,line=" + lineOfAge);
            assertTrue(info.getContents().contains("Integer")
                            || info.getContents().contains("age"),
                    "hover 内容应提到 type 或 field name: " + info.getContents());
        }
    }
}
