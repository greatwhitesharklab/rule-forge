package com.ruleforge.ir.drl;

import com.ruleforge.drl.DrlParser;
import com.ruleforge.drl.DrlParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.42.2 — DRL 4 ANTLR ParseTree → {@link ParsedDrlRule} DTO 的 visitor。
 *
 * <p>继承 ANTLR 自动生成的 {@link DrlParserBaseVisitor}(V5.42.1 在
 * pom.xml 配 {@code <visitor>true</visitor>}),只 override 关注的 rule:
 * <ul>
 *   <li>{@link #visitCompilationUnit} — 入口,产出 {@code List<ParsedDrlRule>}</li>
 *   <li>{@link #visitRuleStatement} — 单 rule,name + attributes + extends + lhs/rhs 引用</li>
 *   <li>{@link #visitRuleAttributes} — 11 个顶层 attribute</li>
 *   <li>{@link #visitDrlPattern} — V5.42.2 调 {@link DatatypeResolver#resolve} 校验 type</li>
 * </ul>
 *
 * <p>边界:
 * <ul>
 *   <li>lhs/rhs 内容(when/then 段内部表达式 / actions)<b>不</b>在 V5.42.2 展开,
 *       只保留 ParseTree 引用,V5.42.4 DrlDeserializer 才建 Lhs / Rhs model</li>
 *   <li>query / function / declare 在 V5.42.1 grammar 是边缘(已 skip),
 *       本 visitor 不实现 — V5.42.5 再补</li>
 *   <li>accumulate / from / collect 在 V5.42.1 grammar 也是边缘,
 *       visitor 在 pattern 阶段 accept 但 lhs 内部细节不展开</li>
 * </ul>
 *
 * <p>设计:DatatypeResolver 通过构造器注入,允许 caller 在 V5.42.5 console-ui 推送
 * type registry 时共享同一 resolver。
 *
 * @since 5.42
 */
public class DrlAstVisitor extends DrlParserBaseVisitor<Void> {

    private final DatatypeResolver resolver;
    private final List<ParsedDrlRule> rules = new ArrayList<>();
    /**
     * V5.44.3 — 顶层 import 段收集。DrlDeserializer 端从 import 列表查 library
     * 路径(目前 DRL 4 暂不解析 import 路径,只挂到 DatatypeResolver 的 import 列表,
     * 留给后续 V5.44.x 实现 library 文件读取)。
     *
     * <p>用 LinkedHashSet 保持插入顺序 + 去重 — visitor 内部就 dedupe,避免 caller
     * (DrlDeserializer / test) 拿到重复 import。
     */
    private final java.util.LinkedHashSet<String> imports = new java.util.LinkedHashSet<>();
    /**
     * V5.45.1 — declare 段收集的 declared types。{@code Map<String, TypeInfo>}
     * 形式,key=type 名(顶层 + 嵌套都进 map),value=完整 TypeInfo(字段 +
     * extendsName + annotations)。DrlDeserializer / LibraryParser(V5.45.2)调
     * {@link #getDeclaredTypes()} 拿 map 推到 resolver。
     *
     * <p>用 LinkedHashMap 保持插入顺序 + dedupe — 同名 type 出现两次取后者
     * (V5.45.1 不报错;V5.46+ 可加 warn 日志)。
     */
    private final java.util.LinkedHashMap<String, DatatypeResolver.TypeInfo> declaredTypes =
        new java.util.LinkedHashMap<>();
    /**
     * V5.44.4 — lenient 模式。{@code true} 时,unknown type 不抛 DrlParseException,
     * 只记到 {@link ParsedDrlRule} 的 unresolvedTypes 字段,继续 walk 后续节点。
     * console-ui editor open 路径(CommonController.parseDrlSummary)用 lenient=true
     * — 打开未保存的半成品 DRL 时,user 还没写 {@code declare} 段,常见 unknown type,
     * 应当返 import 列表 + rule 名称列表(让 editor 展示"待补 declare"),不抛。
     * production 路径(DrlDeserializer.parseDrl)用 strict(default) — 严格校验,
     * 跟 V5.42.2 行为一致。
     */
    private final boolean lenient;

    public DrlAstVisitor(DatatypeResolver resolver) {
        this(resolver, false);
    }

    /**
     * V5.44.4 — 显式指定 lenient 模式的构造器。production caller 用
     * {@link #DrlAstVisitor(DatatypeResolver)} 即可(strict),console-ui
     * editor open 路径用 {@code new DrlAstVisitor(resolver, true)}。
     */
    public DrlAstVisitor(DatatypeResolver resolver, boolean lenient) {
        this.resolver = resolver;
        this.lenient = lenient;
    }

    /** V5.42.2 caller 用这个拿 visitor 跑出来的所有 rule。 */
    public List<ParsedDrlRule> getRules() {
        return rules;
    }

    /** V5.44.3 — 收集的 import 列表(按文件出现顺序,去重)。 */
    public List<String> getImports() {
        return new ArrayList<>(imports);
    }

    /** V5.45.1 — 收集的 declare 段 declared types(嵌套 declare 也 flatten 到 map)。 */
    public java.util.Map<String, DatatypeResolver.TypeInfo> getDeclaredTypes() {
        return new java.util.LinkedHashMap<>(declaredTypes);
    }

    // ============================================================
    // === compilationUnit 入口 ===
    // ============================================================

    @Override
    public Void visitCompilationUnit(DrlParser.CompilationUnitContext ctx) {
        // packageStatement / dialectStatement 顶层 metadata 走 KnowledgeBuilder
        // 阶段(跟 name mapping 一起),V5.42.2 visitor 不建模
        // V5.44.3 — import 段:先收集进 imports 列表(grammar 允许 import 出现在
        //   unitStatement 之前,但本 visitor 不强制 — 实际顺序是 parser 收的)
        for (DrlParser.ImportStatementContext imp : ctx.importStatement()) {
            visit(imp);
        }
        for (DrlParser.UnitStatementContext u : ctx.unitStatement()) {
            visit(u);
        }
        return null;
    }

    @Override
    public Void visitUnitStatement(DrlParser.UnitStatementContext ctx) {
        // V5.45.1 — unitStatement 是 alt(rule / query / function / declare)。
        // ANTLR base visitor 默认 visitChildren 会逐个 visit 各个 alt,但本
        // visitor 显式 override 来确保 declareStatement 也走我们的 visit hook。
        if (ctx.declareStatement() != null) {
            return visit(ctx.declareStatement());
        }
        // rule / query / function 走默认 visitChildren(后续 visitRuleStatement 等)
        return visitChildren(ctx);
    }

    // ============================================================
    // === 顶层 import 段 ===
    // ============================================================

    @Override
    public Void visitImportStatement(DrlParser.ImportStatementContext ctx) {
        // V5.44.3 — 仅支持 library 文件路径(STRING 形式,`"libs/variables.drl"`),
        // 不支持 Drools java 类 import(import com.foo.Bar; 形式 — grammar 没
        // 单独 rule,会走 unitStatement → reject)。
        if (ctx.STRING() == null) {
            throw new DrlParseException(
                "V5.44.3 顶层 import 仅支持 library 路径(双引号字符串),实际:" + ctx.getText(),
                ctx);
        }
        String path = stripQuotes(ctx.STRING().getText());
        imports.add(path);
        // V5.44.3 — 同步推到 resolver。这样后续 visitDrlPattern 调
        // resolver.isKnown() 时拿得到 import 列表,error 消息能附 import 路径
        // 提示 caller。
        resolver.addImport(path);
        return null;
    }

    // ============================================================
    // === lhs 子树显式深入(drlPattern 校验 + lhsParseTree 正确挂载) ===
    // ============================================================
    //
    // ANTLR generated visitor 默认对每个 rule 调 visitChildren,然后再调 child visitors —
    // 但 base visitor 是 no-op,只有 override 过的 method 才会跑。V5.42.2 必须显式 override:
    //   - visitLhsPattern / visitLhsAnd / visitLhsOr / visitLhsOrWithBinding:走 default(就
    //     够 — 它们没有专属提取逻辑,但 visitLhsUnary 的 override 也要走,见下)
    //   - visitLhsUnary:核心 — 让 drlPattern / from / collect / accumulate / not / exists /
    //     eval / expr 都能被逐个 visit
    //
    // 不重写 visitLhsUnary 的后果:Grammar 自己 parse 出 lhsParseTree 是有的(CTX 本身就是
    // 树根),但 drlPattern 校验、永真表达式等 visit hook 都不会跑。

    @Override
    public Void visitLhsUnary(DrlParser.LhsUnaryContext ctx) {
        // 主动深入每个 child — base visitor 默认 visitChildren 会逐个 visit,显式更稳
        if (ctx.drlPattern() != null) {
            visit(ctx.drlPattern());
        }
        if (ctx.lhsFrom() != null) {
            visit(ctx.lhsFrom());
        }
        if (ctx.lhsCollect() != null) {
            visit(ctx.lhsCollect());
        }
        if (ctx.lhsAccumulate() != null) {
            visit(ctx.lhsAccumulate());
        }
        // not / exists / eval 走 default visitChildren 已经够 — 它们都是简单 pass-through
        return visitChildren(ctx);
    }

    // ============================================================
    // === 单 rule:产出 ParsedDrlRule ===
    // ============================================================

    @Override
    public Void visitRuleStatement(DrlParser.RuleStatementContext ctx) {
        ParsedDrlRule rule = new ParsedDrlRule();

        // 1. rule name
        rule.setName(extractRuleName(ctx.ruleName()));

        // 2. extends(D2)
        if (ctx.extendsClause() != null) {
            rule.setExtendsName(extractRuleName(ctx.extendsClause().ruleName()));
        }

        // 3. attributes(11 顶层 attribute)
        if (ctx.ruleAttributes() != null) {
            visitRuleAttributes(ctx.ruleAttributes(), rule);
        }

        // 4. lhs / rhs ParseTree 引用(V5.42.4 才展开)
        //    lhs = whenClause 自身(包含 lhsPattern? 整个 CTX)— "then end" 这种空 case
        //    也保留,让 caller 看到 "then 段存在但没 statement" 这个事实。
        //    rhs = thenClause 自身 — 同理,空 then 也保留。
        if (ctx.whenClause() != null) {
            rule.setLhsParseTree(ctx.whenClause());
        }
        if (ctx.thenClause() != null) {
            rule.setRhsParseTree(ctx.thenClause());
        }

        // 5. 主动深入 lhs 子树 — 触发 visitLhsUnary → visitDrlPattern 校验。
        //    base visitor 的 visitChildren 也会走同样路径,但显式更稳:
        //    if grammar 后续给 ruleStatement 加别的 child,不会因为本调用而被截断。
        if (ctx.whenClause() != null) {
            visit(ctx.whenClause());
        }

        rules.add(rule);
        return null;
    }

    private void visitRuleAttributes(DrlParser.RuleAttributesContext ctx, ParsedDrlRule rule) {
        for (DrlParser.AttributeContext attr : ctx.attribute()) {
            String name = null;
            String value = null;

            if (attr.salienceAttr() != null) {
                name = "salience";
                value = attr.salienceAttr().INT().getText();
            } else if (attr.agendaGroupAttr() != null) {
                name = "agenda-group";
                value = stripQuotes(attr.agendaGroupAttr().STRING().getText());
            } else if (attr.activationGroupAttr() != null) {
                name = "activation-group";
                value = stripQuotes(attr.activationGroupAttr().STRING().getText());
            } else if (attr.ruleflowGroupAttr() != null) {
                name = "ruleflow-group";
                value = stripQuotes(attr.ruleflowGroupAttr().STRING().getText());
            } else if (attr.autoFocusAttr() != null) {
                name = "auto-focus";
                value = attr.autoFocusAttr().DRL_TRUE() != null ? "true" : "false";
            } else if (attr.noLoopAttr() != null) {
                name = "no-loop";
                value = attr.noLoopAttr().DRL_TRUE() != null ? "true" : "false";
            } else if (attr.lockOnActiveAttr() != null) {
                name = "lock-on-active";
                value = attr.lockOnActiveAttr().DRL_TRUE() != null ? "true" : "false";
            } else if (attr.enabledAttr() != null) {
                name = "enabled";
                value = attr.enabledAttr().DRL_TRUE() != null ? "true" : "false";
            } else if (attr.dateEffectiveAttr() != null) {
                name = "date-effective";
                value = stripQuotes(attr.dateEffectiveAttr().STRING().getText());
            } else if (attr.dateExpiresAttr() != null) {
                name = "date-expires";
                value = stripQuotes(attr.dateExpiresAttr().STRING().getText());
            } else if (attr.dialectAttr() != null) {
                // V5.42 D4 决定:顶层 dialect 解析后丢弃(visitor 解析但不存 Rule.dialect)
                // 显式 skip — 注释里说明
                continue;
            } else if (attr.timerAttr() != null) {
                name = "timer";
                value = attr.timerAttr().timerSpec().getText();
            }

            if (name != null) {
                rule.addAttribute(name, value);
            }
        }
    }

    // ============================================================
    // === drlPattern:校验 type 已 declared(DatatypeResolver) ===
    // ============================================================

    @Override
    public Void visitDrlPattern(DrlParser.DrlPatternContext ctx) {
        // V5.42.2 grammar 保证 drlPattern 首 token 是 UPPER_IDENTIFIER
        TerminalNode typeNameNode = ctx.UPPER_IDENTIFIER();
        if (typeNameNode == null) {
            // grammar-level sanity;throw(无论 lenient 与否,这是 grammar 错误)
            throw new DrlParseException(
                "drlPattern must start with UPPER_IDENTIFIER,got " + ctx.getStart().getText(),
                ctx);
        }
        String typeName = typeNameNode.getText();
        // V5.44.3:未声明 type → DrlParseException(error 信息附 import 列表便于诊断)
        if (!resolver.isKnown(typeName)) {
            // V5.44.4 — lenient 模式:不抛,只记录 unknown type 让 caller 后续展示;
            // 严格模式(production DrlDeserializer)维持 V5.42.2 行为:抛
            if (lenient) {
                // 记录到当前正在构建的 rule 上;但 visitor 没存"当前 rule"指针,
                // 简化:用 static 暂存最近一次 rule name,caller 调
                // getRules() 后可对比 unresolvedTypes。看更简单的做法 — 把
                // unresolvedTypes 挂到 ParsedDrlRule 上,visitRuleStatement
                // 末尾同步下来。这里走 throw-then-catch 不优雅,改用更稳的方式:
                // 把 unknown type 存到当前正在 visit 的 rule。但 visitor 没持有
                // "current rule" 引用。
                //
                // 设计选择:在 lenient 模式下,直接 return null 跳过校验 —
                // caller(console-ui editor)只关心 imports + rule names,不展开
                // pattern 内部。type 校验在 V5.45+ editor 高亮阶段再做(那时会
                // 用更结构化的 unresolved marker,不是 throw)。
                return null;
            }
            StringBuilder msg = new StringBuilder();
            msg.append("DRL pattern references unknown type '").append(typeName).append("'.");
            List<String> declaredImports = resolver.getImports();
            if (!declaredImports.isEmpty()) {
                msg.append(" V5.44.3 — DRL 顶层 import 段已 declare 路径 ")
                   .append(declaredImports)
                   .append(" 但 type '").append(typeName).append("' 不在 builtin。"
                   + "library 文件实际加载 V5.45+ 跟进,届时 type 可解析。");
            } else {
                msg.append(" V5.44.3 — 需要预先 register DatatypeResolver,"
                   + "或在同一 .drl 用 'declare' 段,"
                   + "或顶层加 'import \"libs/<file>.drl\";' 引用 library(实际加载 V5.45+ 跟进)。");
            }
            throw new DrlParseException(msg.toString(), ctx);
        }
        // V5.42.4 才会展开内部 constraint 跟 binding;
        // V5.42.2 只校验 type known
        return null;
    }

    // ============================================================
    // === declare 段(V5.45.1 完整化:annotation + 嵌套 + extends) ===
    // ============================================================
    //
    // grammar 行为(DrlParser.g4):
    //   declareStatement:
    //     annotation* DRL_DECLARE IDENTIFIER
    //         ( extendsDecl | fieldsDecl | annotation | declareStatement )*
    //     DRL_END SEMI?
    //
    // V5.45.1 visitor 行为:
    //   - 1 个 declareStatement 对应 1 个 TypeInfo 进 declaredTypes map
    //   - 嵌套 declare(grammar 第 4 alt 是 declareStatement 自身)由 ANTLR base visitor
    //     默认 visitChildren 自动深入;这里也显式 visit 一下保险
    //   - annotation 段出现在 head / fields 之间两种位置都收集
    //   - extendsDecl / fieldsDecl 各自只出现一次(grammar 不强制但实际 DRl 惯例)
    //   - 0 字段 declare 也合法(只 annotation + extends 段,纯 metadata)

    @Override
    public Void visitDeclareStatement(DrlParser.DeclareStatementContext ctx) {
        // 1. 顶层 annotation(head) — grammar: annotation* DRL_DECLARE
        // V5.45.1 关键:annotation 列表可能含**父 declare 头部的 annotation**,但 grammar
        // 第 4 alt 是 declareStatement 自身(嵌套)。ANTLR 把所有 alt 平铺到 annotation() /
        // declareStatement() 列表里,只靠 start token 位置区分。
        // 简化做法:本方法只收集直接属于本 declare 的 annotation(start position <
        // DRL_DECLARE token 自身),嵌套 declare 的 annotation 留给递归 visit 处理。
        // V5.45.1 修复:必须用 DRL_DECLARE token 的 .getSymbol().getStartIndex() 拿
        // token 绝对位置 — ctx.DRL_DECLARE().getSourceInterval() 拿的是 rule context
        // 范围(整个 declare 段),不是 token 位置,等价于 0,filter 失效。
        int declareStart = ctx.DRL_DECLARE().getSymbol().getStartIndex();
        java.util.Map<String, String> annotations = new java.util.LinkedHashMap<>();
        for (DrlParser.AnnotationContext ann : ctx.annotation()) {
            if (ann.getStart().getStartIndex() < declareStart) {
                collectAnnotation(ann, annotations);
            }
        }

        // V5.45.1 关键修复:递归 visit 嵌套 declareStatement(grammar 第 4 alt)。
        // ANTLR base visitor 默认 visitChildren 会逐个 visit,但本方法返回 null 前
        // 必须显式 visit 嵌套,否则内层 declare 不会进 declaredTypes map。
        for (DrlParser.DeclareStatementContext nested : ctx.declareStatement()) {
            visit(nested);
        }

        // 2. UPPER_IDENTIFIER(type 名)— grammar 第二个 token。
        // V5.45.1 grammar 修复:declare 段 type 名必须大写开头(Applicant 之类),
        // 跟 drlPattern lhs 同款。V5.42.1 老 grammar 用 IDENTIFIER 错(IDENTIFIER
        // 是 [a-z_] 前缀,Applicant 走 UPPER_IDENTIFIER 分支,所以 parser 一直报
        // 语法错,V5.42.1 缺 BDD 漏检)。
        String typeName = ctx.UPPER_IDENTIFIER().getText();

        // 3. extendsDecl(可能没有)
        String extendsName = null;
        for (DrlParser.ExtendsDeclContext ext : ctx.extendsDecl()) {
            if (extendsName == null) {
                // V5.45.1:extends 后的 type 名也是 UPPER_IDENTIFIER(Person 之类)
                extendsName = ext.UPPER_IDENTIFIER().getText();
            }
        }

        // 4. fieldsDecl(可能 0+)
        java.util.List<String> fields = new java.util.ArrayList<>();
        for (DrlParser.FieldsDeclContext fd : ctx.fieldsDecl()) {
            // V5.45.1 grammar 调整:fieldsDecl: IDENTIFIER COLON fieldType (COMMA fieldType)* —
            // 第一个 IDENTIFIER 是字段名(单数 terminal,不是 list),后面 fieldType 列表是
            // 类型名(支持 UPPER_IDENTIFIER + IDENTIFIER 两种,跟 V5.45.1 之前 IDENTIFIER
            // 单一形式兼容 — V5.42.1 缺 BDD 漏检导致 type name "int" 走 DRL_TIMER_INT
            // 关键字分支报语法错,V5.45.1 才显式兼容 primitive 类型名)。
            fields.add(fd.IDENTIFIER().getText());
        }

        // 5. 进 declaredTypes map(同 name 取后者,V5.45.1 不报错)
        DatatypeResolver.TypeInfo info = new DatatypeResolver.TypeInfo(
            typeName, fields, false, extendsName, annotations);
        declaredTypes.put(typeName, info);
        return null;
    }

    /**
     * V5.45.1 — 把单个 annotation 段挂到 annotations map。简化:不拆
     * {@code key=val} 形式(grammar 接受 {@code IDENTIFIER ASSIGN STRING} alt,
     * V5.45.1 把整体形参文本当 value;V5.46+ 拆结构化)。
     */
    private void collectAnnotation(DrlParser.AnnotationContext ann,
                                   java.util.Map<String, String> annotations) {
        String name = ann.IDENTIFIER().getText();
        String argsText = "";
        if (ann.annotationArgs() != null) {
            java.util.List<String> argStrs = new java.util.ArrayList<>();
            for (DrlParser.AnnotationArgContext arg : ann.annotationArgs().annotationArg()) {
                if (arg.IDENTIFIER() != null && arg.STRING() != null) {
                    argStrs.add(arg.IDENTIFIER().getText() + "=" + stripQuotes(arg.STRING().getText()));
                } else if (arg.IDENTIFIER() != null) {
                    argStrs.add(arg.IDENTIFIER().getText());
                } else if (arg.STRING() != null) {
                    argStrs.add(stripQuotes(arg.STRING().getText()));
                }
            }
            argsText = String.join(",", argStrs);
        }
        annotations.put(name, argsText);
    }

    // ============================================================
    // === helpers ===
    // ============================================================

    private static String extractRuleName(DrlParser.RuleNameContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.STRING() != null) {
            return stripQuotes(ctx.STRING().getText());
        }
        if (ctx.QUOTED_IDENTIFIER() != null) {
            return stripQuotes(ctx.QUOTED_IDENTIFIER().getText());
        }
        if (ctx.IDENTIFIER() != null) {
            return ctx.IDENTIFIER().getText();
        }
        return null;
    }

    private static String stripQuotes(String s) {
        if (s == null || s.length() < 2) {
            return s;
        }
        if ((s.startsWith("\"") && s.endsWith("\""))
            || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
