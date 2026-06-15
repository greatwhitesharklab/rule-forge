package com.ruleforge.ir.drl;

import com.ruleforge.drl.DrlLexer;
import com.ruleforge.drl.DrlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * V5.78.1 — DRL IDE 共享 service。
 *
 * <p>背景:Phase 14 完整 DRL 编辑器(console-ui)。后端需要给 IDE 端点
 * (parse-with-errors / complete / hover) 提供一致的语法层 + semantic
 * 层 API,避免在 console-app 端再次实现 ANTLR parse 细节。
 *
 * <p>设计要点(跟 V5.44.4 {@code CommonController.parseDrlSummary} 对齐):
 * <ul>
 *   <li>每请求 {@code new DrlLexer + new DrlParser},无共享状态,
 *       线程安全</li>
 *   <li>解析错误用 {@link SyntaxError} (line/column/msg) 收集而非
 *       抛 DrlParseException — IDE 端点是"live editing",半成品 DRL
 *       是常态,不应 500</li>
 *   <li>解析失败时仍尝试从 partial tree 提取 imports + rule names
 *       (ANTLR error recovery 会产生部分 tree;lenient visitor 接受),
 *       跟 V5.44.4 lenient 行为对齐</li>
 * </ul>
 *
 * <p>不在 core 层写 lsp4j / Spring 依赖 — 本类只负责"DRL 文本 →
 * 结构化 IDE 视图"转换,transport 由调用方(console-app
 * DrlIdeController) 决定。Phase 14 选 Option B(custom JSON-RPC +
 * Monaco providers),不走 full lsp4j。
 *
 * <p>无状态:每次方法调用都 new lexer/parser,可在 controller 单例
 * 里长期持有 instance。
 *
 * @since 5.78
 */
public class DrlIdeService {

    /**
     * 顶层 DRL 关键字(用于 autocomplete + hover builtin 提示)。
     *
     * <p>顺序按"DRL 4 子集权威关键字表",跟 {@code DrlLexer.g4} 关键字
     * 段一致;新增关键字须同步两处。{@code declare} 段内的 field type
     * 名(int / String 等)走库内识别,不在此列。
     */
    private static final List<String> TOP_LEVEL_KEYWORDS = List.of(
            "package", "dialect", "import",
            "rule", "query", "function", "declare",
            "when", "then", "end", "return",
            "extends",
            "not", "exists", "eval", "from", "collect", "accumulate",
            "init", "action", "reverse", "result",
            "true", "false", "null"
    );

    private static final List<String> RULE_ATTRIBUTES = List.of(
            "salience", "agenda-group", "activation-group", "ruleflow-group",
            "auto-focus", "no-loop", "lock-on-active", "enabled",
            "date-effective", "date-expires", "timer", "dialect"
    );

    private static final List<String> ACCUMULATE_FUNCS = List.of(
            "count", "sum", "avg", "min", "max"
    );

    // ============================================================
    // === parseWithErrors — 给 IDE live diagnostics 用的入口 ===
    // ============================================================

    /**
     * 解析 DRL 文本,收集所有 syntax errors(不抛)。
     *
     * <p>ANTLR error recovery 允许 parser 在语法错后继续,所以本方法
     * 总是会返回 result(errors 可能为空,也可能非空);调用方应根据
     * {@code errors} 决定是否显示 diagnostics 红线。
     *
     * @param content 完整 DRL 文本
     * @return parse 结果,含 errors + imports + rule names;{@code content}
     *         为 null 时返 errors=空 list 的 result(防御性,console-ui
     *         editor 在 reset 时可能传 null)
     */
    public IdeParseResult parseWithErrors(String content) {
        if (content == null) {
            content = "";
        }
        List<SyntaxError> errors = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        List<ParsedDrlRule> rules = new ArrayList<>();

        // 收集 syntax errors(不抛)
        CollectingErrorListener errorListener = new CollectingErrorListener(errors);
        // 收集 imports(走 AST 走 visitor;不走 ANTLR token 流)
        DatatypeResolver resolver = new DatatypeResolver();
        DrlAstVisitor visitor = new DrlAstVisitor(resolver, true);

        try {
            DrlLexer lexer = new DrlLexer(CharStreams.fromString(content));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DrlParser parser = new DrlParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);
            ParseTree tree = parser.compilationUnit();
            // 即使有 error 也 visit — ANTLR recovery 产 partial tree
            try {
                visitor.visit(tree);
                imports = new ArrayList<>(visitor.getImports());
                rules = new ArrayList<>(visitor.getRules());
            } catch (RuntimeException ex) {
                // visitor 自身异常(不是 parser error)— 当 syntax error 处理
                errors.add(new SyntaxError(0, 0, "visitor: " + ex.getMessage()));
            }
        } catch (RuntimeException ex) {
            // lexer 初始化等极少见异常
            errors.add(new SyntaxError(0, 0, ex.getClass().getSimpleName() + ": " + ex.getMessage()));
        }

        return new IdeParseResult(errors, imports, rules);
    }

    // ============================================================
    // === complete — keyword/builtin completion ===
    // ============================================================

    /**
     * 给定 DRL 文本 + caret offset,返完整 candidate 列表。
     *
     * <p>实现策略:V5.78.1 走"先扫描 keyword + visitor 拿 declared
     * types/fields"双路合并,不做 prefix-based 过滤 — 全部 candidate
     * 都返,由 Monaco provider 在前端做 prefix match(caret 上下文判断
     * 复杂,Server 端 hard-coded 反而会漏)。
     *
     * <p>{@code caretOffset} 暂未用于限定 context(完整 PR TD-14.3 再加);
     * V5.78.1 先收 keyword + declared-field 两路,验证协议。
     *
     * @param content 完整 DRL 文本
     * @param caretOffset caret 位置(0-based char offset)
     * @return 候选 completion 列表,可能为空(空文本也至少返关键字)
     */
    public List<Completion> complete(String content, int caretOffset) {
        // 1. keyword candidate — 直接走 TOP_LEVEL_KEYWORDS
        List<Completion> result = new ArrayList<>();
        for (String kw : TOP_LEVEL_KEYWORDS) {
            result.add(Completion.keyword(kw, "DRL 关键字: " + kw));
        }
        for (String attr : RULE_ATTRIBUTES) {
            result.add(Completion.keyword(attr, "Rule attribute: " + attr));
        }
        for (String fn : ACCUMULATE_FUNCS) {
            result.add(Completion.keyword(fn, "Accumulate 内置函数: " + fn));
        }

        // 2. declared type fields — 走 visitor 内部 declaredTypes map
        // (V5.45.1 起 visitor 维护自己的 declaredTypes,不进 resolver — resolver 只放
        //  builtin + Java import。DrlIdeService 只取 declared 喂 IDE。)
        if (content != null && !content.isEmpty()) {
            try {
                DatatypeResolver resolver = new DatatypeResolver();
                DrlAstVisitor visitor = new DrlAstVisitor(resolver, true);
                DrlLexer lexer = new DrlLexer(CharStreams.fromString(content));
                CommonTokenStream tokens = new CommonTokenStream(lexer);
                DrlParser parser = new DrlParser(tokens);
                parser.removeErrorListeners();
                ParseTree tree = parser.compilationUnit();
                visitor.visit(tree);
                for (DatatypeResolver.TypeInfo ti : visitor.getDeclaredTypes().values()) {
                    for (String fieldName : ti.getFields()) {
                        result.add(Completion.field(
                                fieldName,
                                "Declared field: " + ti.getName() + "." + fieldName
                                        + " (declared in `declare " + ti.getName() + "` 段)"));
                    }
                }
            } catch (RuntimeException ignored) {
                // 解析失败不抛 — complete 半成品 DRL 是常态
            }
        }
        return result;
    }

    // ============================================================
    // === hover — builtin 关键字 + TypeInfo field 提示 ===
    // ============================================================

    /**
     * 给定 DRL 文本 + (line,col),返 hover markdown info。
     *
     * <p>解析逻辑:先扫 {@code content.split("\n")} 取第 {@code line} 行
     * substr,从 {@code col} 位置向左贪心取 keyword-length token(直到
     * 撞到非 identifier char)。
     *
     * <p>实现范围(V5.78.1):
     * <ul>
     *   <li>DRL top-level keyword / rule attribute / accumulate fn:
     *       返 1-2 句中文说明</li>
     *   <li>declared type field:返 type 名字 + field type</li>
     *   <li>其他位置(whitespace / 字符串 / 数字):返 null</li>
     * </ul>
     *
     * @param content 完整 DRL 文本
     * @param line 0-based 行号
     * @param col 0-based 列号
     * @return hover info,无匹配返 null
     */
    public HoverInfo hover(String content, int line, int col) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        String[] lines = content.split("\n", -1);
        if (line < 0 || line >= lines.length) {
            return null;
        }
        String text = lines[line];
        if (col < 0 || col >= text.length()) {
            return null;
        }

        // 1. 取 col 位置起向左贪心识别的 token
        String word = extractIdentifierAt(text, col);
        if (word == null || word.isEmpty()) {
            return null;
        }

        // 2. keyword 检查
        String keywordDoc = keywordDoc(word);
        if (keywordDoc != null) {
            return new HoverInfo(keywordDoc);
        }

        // 3. declared field 检查 — visitor 拿 TypeInfo,查 field name
        try {
            DatatypeResolver resolver = new DatatypeResolver();
            DrlAstVisitor visitor = new DrlAstVisitor(resolver, true);
            DrlLexer lexer = new DrlLexer(CharStreams.fromString(content));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            DrlParser parser = new DrlParser(tokens);
            parser.removeErrorListeners();
            ParseTree tree = parser.compilationUnit();
            visitor.visit(tree);
            for (DatatypeResolver.TypeInfo ti : visitor.getDeclaredTypes().values()) {
                for (String fieldName : ti.getFields()) {
                    if (fieldName.equals(word)) {
                        return new HoverInfo("Declared field: **" + ti.getName()
                                + "." + fieldName + "** (declared in `declare " + ti.getName() + "` 段)");
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 解析失败 hover 返 null
        }

        return null;
    }

    // ============================================================
    // === 内部 helpers ===
    // ============================================================

    /**
     * 从 {@code text} 的 {@code col} 位置起向左贪心取 identifier
     * (regex [A-Za-z_][A-Za-z0-9_-]*;含连字符以兼容 'agenda-group'
     * 等 attribute 名)。
     */
    private static String extractIdentifierAt(String text, int col) {
        Pattern p = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
        Matcher m = p.matcher(text);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (start <= col && col < end) {
                return m.group();
            }
            if (start > col) {
                return null;
            }
        }
        return null;
    }

    /**
     * 关键字中文文档。{@code word} 不在表内返 null。
     */
    private static String keywordDoc(String word) {
        switch (word) {
            case "package":
                return "**package** — DRL 文件顶层命名空间声明";
            case "dialect":
                return "**dialect** — 表达式方言声明(顶层/Rule attribute);RuleForge 走自家 visitor,丢弃";
            case "import":
                return "**import** — 顶层 import 段(library 路径或 Java class FQCN,V5.77+)";
            case "rule":
                return "**rule** — DRL 规则声明:`rule \"Name\" when ... then ... end`";
            case "query":
                return "**query** — DRL 查询声明:`query Name(...) when ... end`";
            case "function":
                return "**function** — 顶层函数:`function T name(args) { body }`";
            case "declare":
                return "**declare** — 类型声明:`declare X fields end`";
            case "when":
                return "**when** — LHS 条件段关键字";
            case "then":
                return "**then** — RHS 动作段关键字";
            case "end":
                return "**end** — 闭合 rule / query / declare 段";
            case "return":
                return "**return** — function body 内 return 语句";
            case "extends":
                return "**extends** — rule 继承 / declare 父类型";
            case "not":
                return "**not** — LHS 否定模式 / 表达式 not in";
            case "exists":
                return "**exists** — LHS 存在模式";
            case "eval":
                return "**eval** — LHS 任意布尔表达式";
            case "from":
                return "**from** — LHS 数据源(`X from Y` / `X from accumulate(...)`)";
            case "collect":
                return "**collect** — LHS 收集模式";
            case "accumulate":
                return "**accumulate** — LHS 累计模式(init/action/reverse/result 4 段)";
            case "init":
                return "**init** — accumulate init 段(声明累加器变量)";
            case "action":
                return "**action** — accumulate action 段(每次匹配 fact 触发)";
            case "reverse":
                return "**reverse** — accumulate reverse 段(fact retract 触发,V5.77 grammar 收,执行 deferred)";
            case "result":
                return "**result** — accumulate result 段(返回表达式)";
            case "count":
                return "**count** — accumulate 内置:计数";
            case "sum":
                return "**sum** — accumulate 内置:求和";
            case "avg":
                return "**avg** — accumulate 内置:平均";
            case "min":
                return "**min** — accumulate 内置:最小值";
            case "max":
                return "**max** — accumulate 内置:最大值";
            case "salience":
                return "**salience** — Rule attribute,优先级(高 → 先)";
            case "agenda-group":
                return "**agenda-group** — Rule attribute,议程组名";
            case "activation-group":
                return "**activation-group** — Rule attribute,激活互斥组";
            case "ruleflow-group":
                return "**ruleflow-group** — Rule attribute,决策流组";
            case "auto-focus":
                return "**auto-focus** — Rule attribute,自动获取焦点";
            case "no-loop":
                return "**no-loop** — Rule attribute,禁止同一 fact 重复触发";
            case "lock-on-active":
                return "**lock-on-active** — Rule attribute,议程组重入锁定";
            case "enabled":
                return "**enabled** — Rule attribute,启用开关(true/false)";
            case "date-effective":
                return "**date-effective** — Rule attribute,生效时间";
            case "date-expires":
                return "**date-expires** — Rule attribute,失效时间";
            case "timer":
                return "**timer** — Rule attribute,定时触发(`timer(int 5s)` / `timer(cron: ...)`)";
            case "true":
                return "**true** — 布尔字面量";
            case "false":
                return "**false** — 布尔字面量";
            case "null":
                return "**null** — 空字面量";
            default:
                return null;
        }
    }

    // ============================================================
    // === Nested types ===
    // ============================================================

    /**
     * parseWithErrors 返回结构。
     */
    public static final class IdeParseResult {
        private final List<SyntaxError> errors;
        private final List<String> imports;
        private final List<ParsedDrlRule> rules;

        public IdeParseResult(List<SyntaxError> errors, List<String> imports, List<ParsedDrlRule> rules) {
            this.errors = errors == null ? Collections.emptyList() : List.copyOf(errors);
            this.imports = imports == null ? Collections.emptyList() : List.copyOf(imports);
            this.rules = rules == null ? Collections.emptyList() : List.copyOf(rules);
        }

        public List<SyntaxError> getErrors() {
            return errors;
        }

        public List<String> getImports() {
            return imports;
        }

        public List<ParsedDrlRule> getRules() {
            return rules;
        }
    }

    /**
     * Completion 候选。
     *
     * <p>{@code kind} 走 LSP {@code CompletionItemKind} 数字编号
     * (Keyword=14, Field=5),让 console-ui 端 Monaco provider 直接映射。
     */
    public static final class Completion {
        public static final int KIND_KEYWORD = 14;
        public static final int KIND_FIELD = 5;

        private final String label;
        private final String detail;
        private final int kind;

        private Completion(String label, String detail, int kind) {
            this.label = label;
            this.detail = detail;
            this.kind = kind;
        }

        public static Completion keyword(String label, String detail) {
            return new Completion(label, detail, KIND_KEYWORD);
        }

        public static Completion field(String label, String detail) {
            return new Completion(label, detail, KIND_FIELD);
        }

        public String getLabel() {
            return label;
        }

        public String getDetail() {
            return detail;
        }

        public int getKind() {
            return kind;
        }
    }

    /**
     * Hover info。{@code contents} 走 markdown,console-ui 端直接 render。
     */
    public static final class HoverInfo {
        private final String contents;

        public HoverInfo(String contents) {
            this.contents = contents;
        }

        public String getContents() {
            return contents;
        }
    }

    // ============================================================
    // === ANTLR error listener ===
    // ============================================================

    /**
     * 把 ANTLR syntax error 收集到 {@code List<SyntaxError>},不打印
     * 到 stderr(DrlDeserializer 行为对齐)。
     */
    private static final class CollectingErrorListener extends BaseErrorListener {
        private final List<SyntaxError> sink;

        CollectingErrorListener(List<SyntaxError> sink) {
            this.sink = sink;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                int line, int charPositionInLine,
                                String msg, RecognitionException e) {
            // 某些 lexer 错(比如裸 EOF 后乱码)会带 null offendingSymbol
            String tokenText = "(unknown)";
            if (offendingSymbol instanceof Token) {
                tokenText = ((Token) offendingSymbol).getText();
            }
            sink.add(new SyntaxError(line, charPositionInLine,
                    "[" + tokenText + "] " + msg));
        }
    }
}
