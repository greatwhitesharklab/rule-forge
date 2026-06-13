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

    public DrlAstVisitor(DatatypeResolver resolver) {
        this.resolver = resolver;
    }

    /** V5.42.2 caller 用这个拿 visitor 跑出来的所有 rule。 */
    public List<ParsedDrlRule> getRules() {
        return rules;
    }

    // ============================================================
    // === compilationUnit 入口 ===
    // ============================================================

    @Override
    public Void visitCompilationUnit(DrlParser.CompilationUnitContext ctx) {
        // packageStatement / dialectStatement 顶层 metadata 走 KnowledgeBuilder
        // 阶段(跟 name mapping 一起),V5.42.2 visitor 不建模
        for (DrlParser.UnitStatementContext u : ctx.unitStatement()) {
            visit(u);
        }
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
            // grammar-level sanity;throw
            throw new DrlParseException(
                "drlPattern must start with UPPER_IDENTIFIER,got " + ctx.getStart().getText(),
                ctx);
        }
        String typeName = typeNameNode.getText();
        // V5.42 D4:未声明 type → DrlParseException
        if (!resolver.isKnown(typeName)) {
            throw new DrlParseException(
                "DRL pattern references unknown type '" + typeName + "'. "
                + "V5.42 D4:Drools 'import' 不支持,需要预先 register DatatypeResolver 或同 .drl 用 'declare' 段。",
                ctx);
        }
        // V5.42.4 才会展开内部 constraint 跟 binding;
        // V5.42.2 只校验 type known
        return null;
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
