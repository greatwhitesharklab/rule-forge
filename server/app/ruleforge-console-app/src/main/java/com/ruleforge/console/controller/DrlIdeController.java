package com.ruleforge.console.controller;

import com.ruleforge.ir.drl.DrlIdeService;
import com.ruleforge.ir.drl.ParsedDrlRule;
import com.ruleforge.ir.drl.SyntaxError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V5.78.1 — DRL IDE 端点(console-app 端,Phase 14 Option B 选型)。
 *
 * <p>背景:Phase 14 完整 DRL 编辑器(console-ui)。LSP server 走 full
 * protocol 风险大(选型见 {@code docs/notes/v578-drl-ide-plan.md}),
 * 选 **Option B**:Spring REST + 3 端点,console-ui 用 Monaco
 * {@code registerCompletionItemProvider} / {@code registerHoverProvider} /
 * {@code setModelMarkers} 直连。
 *
 * <p>端点:
 * <ul>
 *   <li>{@code POST /ruleforge/ide/parse} — 解析 DRL,返 syntax errors
 *       (line/col/msg)+ imports + rules(给 IDE diagnostics 红线用)</li>
 *   <li>{@code POST /ruleforge/ide/complete} — 返 keyword + 声明 type field
 *       completion 候选(给 IDE autocomplete 弹窗用)</li>
 *   <li>{@code POST /ruleforge/ide/hover} — 返 markdown hover(给 IDE
 *       hover popup 用)</li>
 * </ul>
 *
 * <p>底层逻辑全部委托 {@link DrlIdeService}(core 模块,无 Spring 依赖),
 * controller 只做 transport / 序列化。
 *
 * <p>性能:live editing 场景,client 端 debounce 300ms;DrlIdeService
 * 每请求 new lexer/parser,1KB DRL 解析 < 5ms。
 *
 * @since 5.78
 */
@Slf4j
@RestController
@RequestMapping("/${ruleforge.root.path}/ide")
@RequiredArgsConstructor
public class DrlIdeController {

    private final DrlIdeService drlIdeService;

    // ============================================================
    // === parse — diagnostics 红线 ===
    // ============================================================

    /**
     * 解析 DRL,返 syntax errors + imports + rules。
     *
     * <p>请求:
     * <pre>
     * { "content": "rule \"R1\" when ... end" }
     * </pre>
     *
     * <p>响应:
     * <pre>
     * {
     *   "errors": [ { "line": 1, "column": 5, "message": "[foobar] ..." }, ... ],
     *   "imports": ["libs/x.drl"],
     *   "rules": [ { "name": "R1" }, ... ]
     * }
     * </pre>
     *
     * <p>失败语义(跟 V5.44.4 {@code /loadDrl} 对齐):
     * 半成品 DRL 是常态,errors 非空仍 200;只有反序列化层(本方法外)
     * 错才 400。
     */
    @PostMapping("/parse")
    public Map<String, Object> parse(@RequestBody IdeRequest req) {
        String content = req == null ? "" : req.content();
        if (content == null) {
            content = "";
        }
        log.debug("ide.parse: {} bytes", content.length());
        DrlIdeService.IdeParseResult result = drlIdeService.parseWithErrors(content);

        Map<String, Object> resp = new LinkedHashMap<>();
        List<Map<String, Object>> errs = new ArrayList<>();
        for (SyntaxError e : result.getErrors()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("line", e.getLine());
            m.put("column", e.getColumn());
            m.put("message", e.getMessage());
            errs.add(m);
        }
        resp.put("errors", errs);
        resp.put("imports", result.getImports());
        List<Map<String, Object>> rules = new ArrayList<>();
        for (ParsedDrlRule r : result.getRules()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.getName());
            rules.add(m);
        }
        resp.put("rules", rules);
        return resp;
    }

    // ============================================================
    // === complete — autocomplete 候选 ===
    // ============================================================

    /**
     * 返 completion 候选。
     *
     * <p>请求:
     * <pre>
     * { "content": "rule \"R1\" ", "caretOffset": 11 }
     * </pre>
     *
     * <p>响应:
     * <pre>
     * { "completions": [ { "label": "when", "detail": "DRL 关键字: when", "kind": 14 }, ... ] }
     * </pre>
     *
     * <p>{@code kind} 走 LSP {@code CompletionItemKind} 数字编号
     * (Keyword=14, Field=5);client 端 Monaco provider 直接映射。
     */
    @PostMapping("/complete")
    public Map<String, Object> complete(@RequestBody CompleteRequest req) {
        String content = req == null ? "" : req.content();
        if (content == null) {
            content = "";
        }
        int caretOffset = req == null ? 0 : Math.max(0, req.caretOffset());
        log.debug("ide.complete: {} bytes, caret={}", content.length(), caretOffset);

        List<DrlIdeService.Completion> completions = drlIdeService.complete(content, caretOffset);
        List<Map<String, Object>> out = new ArrayList<>();
        for (DrlIdeService.Completion c : completions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", c.getLabel());
            m.put("detail", c.getDetail());
            m.put("kind", c.getKind());
            out.add(m);
        }
        return Map.of("completions", out);
    }

    // ============================================================
    // === hover — hover popup ===
    // ============================================================

    /**
     * 返 hover markdown。无匹配返 {@code {"contents": null}}。
     *
     * <p>请求:
     * <pre>
     * { "content": "rule \"R1\" when ...", "line": 0, "col": 1 }
     * </pre>
     *
     * <p>响应:
     * <pre>
     * { "contents": "**rule** — DRL 规则声明:..." }
     * </pre>
     *
     * <p>{@code line} 0-based(跟 IDE editor 视图一致);
     * {@code col} 0-based。
     */
    @PostMapping("/hover")
    public Map<String, Object> hover(@RequestBody HoverRequest req) {
        String content = req == null ? "" : req.content();
        if (content == null) {
            content = "";
        }
        int line = req == null ? 0 : Math.max(0, req.line());
        int col = req == null ? 0 : Math.max(0, req.col());
        log.debug("ide.hover: {} bytes, line={}, col={}", content.length(), line, col);

        DrlIdeService.HoverInfo info = drlIdeService.hover(content, line, col);
        if (info == null) {
            return Map.of("contents", (Object) null);
        }
        return Map.of("contents", info.getContents());
    }

    // ============================================================
    // === Request DTOs (record,Java 17) ===
    // ============================================================

    /**
     * 通用 IDE 请求 — V5.78.1 简化:client 端 fetch 一次性发 content
     * (小,1KB 量级),不维护 docId / version。
     */
    public record IdeRequest(String content) {}

    public record CompleteRequest(String content, int caretOffset) {}

    public record HoverRequest(String content, int line, int col) {}
}
