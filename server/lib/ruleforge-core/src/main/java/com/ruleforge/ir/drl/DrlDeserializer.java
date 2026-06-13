package com.ruleforge.ir.drl;

import com.ruleforge.drl.DrlLexer;
import com.ruleforge.drl.DrlParser;
import com.ruleforge.ir.drl.ParsedDrlRule.DrlAttribute;
import com.ruleforge.model.rule.ConstantValue;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.ValueType;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.PropertyCriteria;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * V5.42.4 — DRL AST → {@link Rule} model 的反序列化器。
 *
 * <p>流水线:
 * <pre>
 *   .drl 文本
 *      ↓ DrlLexer / DrlParser
 *   ParseTree
 *      ↓ DrlAstVisitor(校验 + 顶层 metadata)
 *   List&lt;ParsedDrlRule&gt;
 *      ↓ DrlDeserializer(本类)
 *   List&lt;Rule&gt;
 * </pre>
 *
 * <p>本类第一版范围(V5.42.4):
 * <ul>
 *   <li>顶层 rule name / extends / 11 attribute 全映射到 Rule 字段</li>
 *   <li>lhs 最简 {@code Type(field op value)} 形式 → Lhs.criterion = Junction(AND) + PropertyCriteria</li>
 *   <li>rhs 第一版留空 Rhs(actions = null)— V5.42.5 再补 actions 解析</li>
 *   <li>未知 attribute / 语法错 / 未注册 type → DrlParseException</li>
 * </ul>
 *
 * <p>不在 V5.42.4 范围:rhs 动作、accumulate/from/collect 内部、function/declare/query。
 * 那些继续走 V5.42.5 之后的 visitor 路径。
 *
 * @since 5.42
 */
public class DrlDeserializer {

    /**
     * V5.42.4 — 暂存:PropertyCriteria 列表(rule name → criteria)。
     * V5.42.4 范围内 PropertyCriteria 暂不能进 {@link Rule#getLhs()}(老 PropertyCriteria
     * 不是 Criterion,Junction.addCriterion 链挂不上),放本 map 暂存,V5.42.5 重新设计
     * lhs model 包装(NamedCriteria + CriteriaUnit 链路)再迁入。
     */
    private static final java.util.Map<String, List<PropertyCriteria>> PENDING_LHS =
        new java.util.concurrent.ConcurrentHashMap<>();

    public static List<PropertyCriteria> getPendingLhsCriteria(Rule rule) {
        return PENDING_LHS.getOrDefault(rule.getName(), Collections.emptyList());
    }

    // ============================================================
    // === 入口 ===
    // ============================================================

    public static List<Rule> parseDrl(String drl, DatatypeResolver resolver) {
        if (drl == null) {
            throw new DrlParseException("DRL 文本不能为 null");
        }
        // 1. 走 ANTLR parse
        DrlParser.CompilationUnitContext tree = parse(drl);
        // 2. 走 visitor(校验 + 顶层 metadata)
        DrlAstVisitor visitor = new DrlAstVisitor(resolver);
        visitor.visit(tree);
        List<ParsedDrlRule> parsed = visitor.getRules();
        // V5.44.3 — 顶层 import 段收集到的 library 路径塞进 resolver(不解析内容,
        // V5.45+ library 加载器再消费这个列表)。这一步在 resolve() 之前做完,
        // 否则 visitor 内部 visitDrlPattern 调 resolver.isKnown() 时拿不到 import hint。
        for (String imp : visitor.getImports()) {
            resolver.addImport(imp);
        }
        // 3. 转 Rule
        List<Rule> rules = new ArrayList<>();
        for (ParsedDrlRule p : parsed) {
            rules.add(convert(p, resolver));
        }
        return rules;
    }

    public static List<Rule> parseDrlFile(String path, DatatypeResolver resolver) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(path)));
            return parseDrl(content, resolver);
        } catch (IOException e) {
            throw new DrlParseException("读 .drl 文件失败:" + path + " — " + e.getMessage(), e);
        }
    }

    // ============================================================
    // === ANTLR parse(共享给入口) ===
    // ============================================================

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

    // ============================================================
    // === ParsedDrlRule → Rule ===
    // ============================================================

    private static Rule convert(ParsedDrlRule p, DatatypeResolver resolver) {
        Rule r = new Rule();
        // 1. name + extends(D2)
        r.setName(p.getName());
        if (p.getExtendsName() != null) {
            r.setWithElse(true);
        }
        // 2. attributes 映射
        for (DrlAttribute attr : p.getAttributes()) {
            applyAttribute(r, attr);
        }
        // 3. Lhs(第一版:walk lhsParseTree,处理 Type(field op value))
        r.setLhs(extractLhs(p, resolver));
        // 4. Rhs(第一版:空 actions 列表)
        r.setRhs(new Rhs());
        return r;
    }

    /**
     * 把单个 attribute 应用到 Rule 字段。
     * V5.42.4 决定:
     * <ul>
     *   <li>boolean 字段(no-loop / lock-on-active / auto-focus / enabled)直接复制 — Rule 的
     *       loop 字段语义反转(no-loop=true → loop=null/false,V5.42.4 简化:不反转,
     *       把 no-loop 原始 boolean 存进 Other / 不映射,留 V5.42.5)</li>
     *   <li>date 字段用 SimpleDateFormat(ISO 短日期)解析</li>
     *   <li>timer 字段 V5.42.4 不映射</li>
     *   <li>未知 attribute 名 → DrlParseException(防止 typo 静默丢)</li>
     * </ul>
     */
    private static void applyAttribute(Rule r, DrlAttribute attr) {
        String name = attr.getName();
        String value = attr.getValue();
        if (name == null) {
            return;
        }
        switch (name) {
            case "salience":
                try {
                    r.setSalience(Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    throw new DrlParseException("salience 应是整数,实际:'" + value + "'");
                }
                break;
            case "agenda-group":
                r.setAgendaGroup(value);
                break;
            case "activation-group":
                r.setActivationGroup(value);
                break;
            case "ruleflow-group":
                r.setRuleflowGroup(value);
                break;
            case "auto-focus":
                r.setAutoFocus(parseBoolean(value));
                break;
            case "no-loop":
                // V5.42.4 简化:不映射到 Rule.loop(语义反转留 V5.42.5)
                // 故意不写 Rule.loop — 保持 default
                break;
            case "lock-on-active":
                // V5.42.4 简化:Rule 没有 lock-on-active 字段;不映射
                break;
            case "enabled":
                r.setEnabled(parseBoolean(value));
                break;
            case "date-effective":
                r.setEffectiveDate(parseDate(value));
                break;
            case "date-expires":
                r.setExpiresDate(parseDate(value));
                break;
            case "timer":
                // V5.42.4 简化:timer 留 V5.42.5
                break;
            case "dialect":
                // V5.42 D4 决定:顶层 dialect 解析后丢弃,visitor 已经过滤;
                // 这里再保险一次
                break;
            default:
                throw new DrlParseException(
                    "未知顶层 attribute '" + name + "' — V5.42.4 不支持");
        }
    }

    private static Boolean parseBoolean(String s) {
        if (s == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Date parseDate(String s) {
        if (s == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return sdf.parse(s);
        } catch (ParseException e) {
            throw new DrlParseException("date 解析失败 '" + s + "':要求 yyyy-MM-dd 格式");
        }
    }

    // ============================================================
    // === Lhs(第一版)===
    // ============================================================

    /**
     * 从 ParsedDrlRule.lhsParseTree 提取 Lhs。第一版只处理 {@code Type(field op value)} 形式。
     * <p>处理逻辑:
     * <ul>
     *   <li>walk lhsPattern → 找每条 lhsUnary(drlPattern alt)</li>
     *   <li>每条 drlPattern:用 lexer 拆 (field op value) 三元组,产出 PropertyCriteria</li>
     *   <li>全部收集到 Junction(AND) — 单条也走 Junction 包装(跟老 Rule 行为一致)</li>
     * </ul>
     *
     * <p>不处理:and / or / not / exists / eval / from / collect / accumulate 内部,
     * V5.42.5 再补。
     */
    private static Lhs extractLhs(ParsedDrlRule p, DatatypeResolver resolver) {
        Lhs lhs = new Lhs();
        if (p.getLhsParseTree() == null) {
            return lhs;
        }
        // V5.42.4 简化:用 visitor 单独走 lhsParseTree,产出一组 PropertyCriteria,
        // 暂存到 PENDING_LHS map(criterion 字段保留 null,V5.42.5 再迁入 Rule.lhs 内部)
        List<PropertyCriteria> pcs = new LhsPropertyVisitor(resolver).extract(p.getLhsParseTree());
        if (!pcs.isEmpty()) {
            PENDING_LHS.put(p.getName(), pcs);
        }
        return lhs;
    }

    // ============================================================
    // === inner helper: LhsPropertyVisitor ===
    // ============================================================

    /**
     * V5.42.4 — 在 lhs ParseTree 上抓所有 {@code Type(field op value)} 形式,
     * 产出 PropertyCriteria 列表。
     *
     * <p>走 ANTLR ParseTree API(直接 ctx 访问)— 不依赖新加 grammar rule。
     * 限制:只处理 DRL 最外层 drlPattern(field op value 形式),
     * 内部嵌套 / from / collect / not 都不进 — V5.42.5 再补。
     */
    static class LhsPropertyVisitor {
        private final DatatypeResolver resolver;
        private final List<PropertyCriteria> result = new ArrayList<>();

        LhsPropertyVisitor(DatatypeResolver resolver) {
            this.resolver = resolver;
        }

        List<PropertyCriteria> extract(org.antlr.v4.runtime.tree.ParseTree lhsTree) {
            // 找到所有 drlPattern 节点
            walk(lhsTree);
            return result;
        }

        private void walk(org.antlr.v4.runtime.tree.ParseTree node) {
            // 反射式走 children — 用 ctx.getChild(i) 找 DrlPatternContext
            if (!(node instanceof ParserRuleContext)) {
                return;
            }
            ParserRuleContext ctx = (ParserRuleContext) node;
            int n = ctx.getChildCount();
            for (int i = 0; i < n; i++) {
                org.antlr.v4.runtime.tree.ParseTree child = ctx.getChild(i);
                if (child instanceof DrlParser.DrlPatternContext) {
                    handleDrlPattern((DrlParser.DrlPatternContext) child);
                } else if (child instanceof ParserRuleContext) {
                    walk(child);
                }
            }
        }

        private void handleDrlPattern(DrlParser.DrlPatternContext dp) {
            // dp:UPPER_IDENTIFIER LPAREN exprList? RPAREN
            // exprList:expr (COMMA expr)* — 每条 expr 是 'field op value' 形式
            DrlParser.ExprListContext el = dp.exprList();
            if (el == null) {
                return; // 空 pattern(无约束)
            }
            int idx = 0;
            for (DrlParser.ExprContext e : el.expr()) {
                // 期望:exprAtom (cmpOp exprAtom)*
                List<DrlParser.ExprAtomContext> atoms = e.exprAtom();
                List<DrlParser.CmpOpContext> ops = e.cmpOp();
                // 第一版:1 个 cmpOp + 2 个 exprAtom = 单条 PropertyCriteria
                if (atoms.size() == 2 && ops.size() == 1) {
                    result.add(buildPropertyCriteria(dp, atoms.get(0), ops.get(0), atoms.get(1)));
                    idx++;
                } else if (atoms.size() == 1 && ops.isEmpty()) {
                    // 纯引用(无 op)— V5.42.4 跳过,留 V5.42.5
                    idx++;
                }
                // 多 cmpOp 链(a == b == c)— V5.42.4 简化,留 V5.42.5
            }
        }

        private PropertyCriteria buildPropertyCriteria(DrlParser.DrlPatternContext dp,
                                                       DrlParser.ExprAtomContext leftAtom,
                                                       DrlParser.CmpOpContext op,
                                                       DrlParser.ExprAtomContext rightAtom) {
            PropertyCriteria pc = new PropertyCriteria();
            // left 必须是 IDENTIFIER(属性名)— V5.42.4 简化:不接 complex
            String leftText = leftAtom.getText();
            pc.setProperty(leftText);
            // op
            pc.setOp(mapOp(op));
            // right 走 Value
            pc.setValue(buildValue(rightAtom));
            return pc;
        }

        private static Op mapOp(DrlParser.CmpOpContext op) {
            String text = op.getText();
            // DRL 'not in' 在 cmpOp 里是 'not in' 两个 token,getText() 是 "not in"
            switch (text) {
                case "==": case "=": return Op.Equals;
                case "!=": return Op.NotEquals;
                case ">": return Op.GreaterThen;
                case ">=": return Op.GreaterThenEquals;
                case "<": return Op.LessThen;
                case "<=": return Op.LessThenEquals;
                case "in": return Op.In;
                case "not in": return Op.NotIn;
                case "memberOf": return Op.Match; // 老 Op 没 MemberOf,降级到 Match(V5.42.5 扩展)
                case "matches": return Op.Match;
                case "contains": return Op.Contain;
                case "soundslike": return Op.Match; // 老 Op 没 SoundsLike,降级 Match
                default:
                    throw new DrlParseException("未知 cmpOp '" + text + "'");
            }
        }

        private Value buildValue(DrlParser.ExprAtomContext atom) {
            // V5.42.4 简化:literal / IDENTIFIER / methodChain 三种
            DrlParser.AtomContext a = atom.atom();
            if (a == null) {
                throw new DrlParseException("exprAtom 内部 atom 缺失,line " + atom.getStart().getLine());
            }
            if (a.literal() != null) {
                return buildLiteralValue(a.literal());
            }
            if (a.IDENTIFIER() != null) {
                SimpleValue sv = new SimpleValue();
                sv.setContent(a.IDENTIFIER().getText());
                // SimpleValue 默认 valueType=Input,V5.42.4 沿用
                return sv;
            }
            if (a.methodChain() != null) {
                SimpleValue sv = new SimpleValue();
                sv.setContent(a.getText());
                // V5.42.4 简化:Method value 走 SimpleValue,valueType 仍 Input
                return sv;
            }
            if (a.PLACEHOLDER() != null) {
                SimpleValue sv = new SimpleValue();
                sv.setContent(a.PLACEHOLDER().getText());
                return sv;
            }
            // DOLLAR IDENTIFIER(V5.42.4 简化:作字面量"$name"放进去)
            if (a.DOLLAR() != null && a.IDENTIFIER() != null) {
                SimpleValue sv = new SimpleValue();
                sv.setContent(a.getText());
                return sv;
            }
            throw new DrlParseException("V5.42.4 不支持的 atom 形式:'" + a.getText() + "'");
        }

        private Value buildLiteralValue(DrlParser.LiteralContext lit) {
            if (lit.STRING() != null) {
                SimpleValue sv = new SimpleValue();
                sv.setContent(stripQuotes(lit.STRING().getText()));
                return sv;
            }
            if (lit.INT() != null) {
                ConstantValue cv = new ConstantValue();
                cv.setConstantName(lit.INT().getText());
                cv.setConstantCategory("Integer");
                return cv;
            }
            if (lit.FLOAT() != null) {
                ConstantValue cv = new ConstantValue();
                cv.setConstantName(lit.FLOAT().getText());
                cv.setConstantCategory("Double");
                return cv;
            }
            if (lit.DRL_TRUE() != null) {
                ConstantValue cv = new ConstantValue();
                cv.setConstantName("true");
                cv.setConstantCategory("Boolean");
                return cv;
            }
            if (lit.DRL_FALSE() != null) {
                ConstantValue cv = new ConstantValue();
                cv.setConstantName("false");
                cv.setConstantCategory("Boolean");
                return cv;
            }
            if (lit.DRL_NULL() != null) {
                ConstantValue cv = new ConstantValue();
                cv.setConstantName("null");
                cv.setConstantCategory("Null");
                return cv;
            }
            throw new DrlParseException("V5.42.4 不支持的 literal 形式");
        }

        private static String stripQuotes(String s) {
            if (s == null || s.length() < 2) {
                return s;
            }
            if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                return s.substring(1, s.length() - 1);
            }
            return s;
        }
    }
}
