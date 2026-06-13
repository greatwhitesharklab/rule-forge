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
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.FromLeftPart;
import com.ruleforge.model.rule.lhs.JunctionType;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.MultiCondition;
import com.ruleforge.model.rule.lhs.PropertyCriteria;
import com.ruleforge.model.rule.lhs.StatisticType;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
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
     *   <li>全部转成 {@link Criteria}(Left=VariableLeftPart 持 property 名),装进
     *       {@link And} Junction,挂到 {@link Lhs#setCriterion} — 单条也走 Junction 包装
     *       (跟老 Rule 行为一致)</li>
     * </ul>
     *
     * <p>不处理:and / or / not / exists / eval / from / collect / accumulate 内部,
     * V5.42.5 再补。
     *
     * <p><b>V5.51.2 migration 收口</b>:V5.42.4 暂存 PENDING_LHS 字段 + getPendingLhsCriteria
     * 已删,PropertyCriteria 全部走 Lhs.criterion(And → Criteria)链。
     */
    private static Lhs extractLhs(ParsedDrlRule p, DatatypeResolver resolver) {
        Lhs lhs = new Lhs();
        if (p.getLhsParseTree() == null) {
            return lhs;
        }
        LhsPropertyVisitor visitor = new LhsPropertyVisitor(resolver);
        visitor.extract(p.getLhsParseTree());
        List<PropertyCriteria> pcs = visitor.getPropertyCriterias();
        List<FromLeftPart> fromParts = visitor.getFromLeftParts();
        if (pcs.isEmpty() && fromParts.isEmpty()) {
            return lhs;
        }
        // V5.51.2:PropertyCriteria → Criteria(Left=VariableLeftPart(property))→ And Junction
        // V5.52.1:FromLeftPart(DRL from/collect/accumulate) → Criteria(Left.leftPart=FromLeftPart)
        And and = new And();
        for (PropertyCriteria pc : pcs) {
            and.addCriterion(toCriteria(pc));
        }
        for (FromLeftPart fp : fromParts) {
            and.addCriterion(toFromCriteria(fp));
        }
        lhs.setCriterion(and);
        return lhs;
    }

    /**
     * V5.52.1:把 FromLeftPart 包成 lhs model {@link Criteria}。Left 用
     * {@code LeftType.variable} 作占位(FromLeftPart 没有专属 LeftType 值,
     * {@code BuildContextImpl.getObjectType} 看 {@code leftPart instanceof AbstractLeftPart}
     * 路由,不看 type)。
     *
     * <p>op = {@link Op#NotEquals} + value = null(语义:"from-source 求出非 null
     * 即 binding 成功")。{@link com.ruleforge.runtime.assertor.NotEqualsAssertor}
     * 在 right=null 时返 left != null,正是"binding 成功"的判定。
     */
    private static Criteria toFromCriteria(FromLeftPart fp) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setLeftPart(fp);
        left.setType(LeftType.variable);
        c.setLeft(left);
        c.setOp(Op.NotEquals);
        ConstantValue nullVal = new ConstantValue();
        nullVal.setConstantName("null");
        nullVal.setConstantCategory("Null");
        c.setValue(nullVal);
        return c;
    }

    /**
     * V5.51.2:把 DRL shorthand {@code PropertyCriteria(field, op, value)}
     * 转成 lhs model {@link Criteria}。Left 用 {@link VariableLeftPart} 持
     * variableName/variableLabel(都用 property 名 — DRL shorthand 没有 binding
     * 类型信息,跟 DecisionTable 的 CellContentBuilder 路径对齐)。
     */
    private static Criteria toCriteria(PropertyCriteria pc) {
        Criteria c = new Criteria();
        Left left = new Left();
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableName(pc.getProperty());
        part.setVariableLabel(pc.getProperty());
        left.setLeftPart(part);
        left.setType(LeftType.variable);
        c.setLeft(left);
        c.setOp(pc.getOp());
        c.setValue(pc.getValue());
        return c;
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
        /** V5.52.1:DRL from / collect / accumulate 产出的 FromLeftPart(走 AbstractLeftPart 链) */
        private final List<FromLeftPart> fromParts = new ArrayList<>();

        LhsPropertyVisitor(DatatypeResolver resolver) {
            this.resolver = resolver;
        }

        List<PropertyCriteria> extract(org.antlr.v4.runtime.tree.ParseTree lhsTree) {
            // 找到所有 drlPattern 节点
            walk(lhsTree);
            return result;
        }

        /** V5.52.1:暴露 FromLeftPart 给外层 extractLhs 包 Criteria。 */
        List<FromLeftPart> getFromLeftParts() {
            return fromParts;
        }

        /** V5.51.2:暴露 PropertyCriteria 给外层(读 result 跟 getFromLeftParts 同形态)。 */
        List<PropertyCriteria> getPropertyCriterias() {
            return result;
        }

        private void walk(org.antlr.v4.runtime.tree.ParseTree node) {
            // 反射式走 children — 用 ctx.getChild(i) 找 DrlPatternContext / LhsFromContext /
            //   LhsCollectContext / LhsAccumulateContext
            if (!(node instanceof ParserRuleContext)) {
                return;
            }
            ParserRuleContext ctx = (ParserRuleContext) node;
            int n = ctx.getChildCount();
            for (int i = 0; i < n; i++) {
                org.antlr.v4.runtime.tree.ParseTree child = ctx.getChild(i);
                if (child instanceof DrlParser.DrlPatternContext) {
                    handleDrlPattern((DrlParser.DrlPatternContext) child);
                } else if (child instanceof DrlParser.LhsFromContext) {
                    // V5.52.1:DRL from $stream
                    handleLhsFrom((DrlParser.LhsFromContext) child);
                } else if (child instanceof DrlParser.LhsCollectContext) {
                    // V5.52.2:DRL from collect(...)
                    handleLhsCollect((DrlParser.LhsCollectContext) child);
                } else if (child instanceof DrlParser.LhsAccumulateContext) {
                    // V5.52.3:DRL from accumulate(...)
                    handleLhsAccumulate((DrlParser.LhsAccumulateContext) child);
                } else if (child instanceof ParserRuleContext) {
                    walk(child);
                }
            }
        }

        /**
         * V5.52.1:DRL {@code $a : X(field op value) from $stream} 形态。
         *
         * <p>grammar LhsFrom 4 alt:
         * <ol>
         *   <li>{@code drlPattern FROM drlPattern} — 源是另一 type ref(`from applicants`)</li>
         *   <li>{@code drlPattern FROM expr} — 源是表达式(`from $stream` /
         *       `from $loan.getApplicants()`)— 本 sprint 接</li>
         *   <li>{@code lhsAtomic FROM lhsAtomic} — 老 binding-only 形式(本 sprint 不接)</li>
         *   <li>{@code lhsAtomic FROM expr} — 同 3(本 sprint 不接)</li>
         * </ol>
         * 本 sprint **接 alt 1 + alt 2**。alt 3/4 deferred 到 V5.53+。
         *
         * <p>binding source({@code $stream})的 source resolution 是 runtime 责任
         * (Utils.getObjectProperty 在 obj 上找 {@code $stream})。本 sprint **接受
         * 静默失效**:deserializer 不验,陪跑测试时真用会显式抛 RuleException(R1)。
         */
        private void handleLhsFrom(DrlParser.LhsFromContext ctx) {
            List<DrlParser.DrlPatternContext> dps = ctx.drlPattern();
            DrlParser.ExprContext sourceExpr = ctx.expr();
            // grammar LhsFrom 4 alt:
            //   alt 1: drlPattern FROM drlPattern → dps.size==2, sourceExpr==null
            //   alt 2: drlPattern FROM expr         → dps.size==1, sourceExpr!=null
            //   alt 3: lhsAtomic FROM lhsAtomic     → dps empty
            //   alt 4: lhsAtomic FROM expr          → dps empty
            // 本 sprint 接 alt 1 + alt 2(最常见 DRL 形态)— alt 3/4 deferred 到 V5.53+。
            if (dps.isEmpty()) {
                throw new DrlParseException("V5.52.1 only supports 'drlPattern FROM drlPattern|expr' "
                    + "for LhsFrom (DRL 'from $stream' 形态),"
                    + "line " + ctx.getStart().getLine() + " 实际 'lhsAtomic' 形态(alt 3/4) — 等 V5.53+");
            }
            if (dps.size() != 1 || sourceExpr == null) {
                throw new DrlParseException("V5.52.1 LhsFrom alt 解析异常:"
                    + " dps.size=" + dps.size() + " expr=" + (sourceExpr != null)
                    + " line " + ctx.getStart().getLine());
            }
            DrlParser.DrlPatternContext outer = dps.get(0);

            FromLeftPart fp = new FromLeftPart();
            // outer type 来源:UPPER_IDENTIFIER
            String outerType = outer.UPPER_IDENTIFIER().getText();
            // 校验 outer type 已在 resolver 注册(visitor 走完之后,DatatypeResolver 已经
            //   看过 outer,UPPER_IDENTIFIER 已 resolve 过)
            if (!resolver.isKnown(outerType)) {
                throw new DrlParseException("from $stream 外层 type '" + outerType
                    + "' 未注册,line " + outer.getStart().getLine());
            }
            fp.setVariableCategory(outerType);
            // variableName 走 source expr 的字面 text 例 "$stream" / "$loan.getApplicants()"
            //   简化:取 expr.getText()(涵盖 methodChain 整段)— runtime 端
            //   Utils.getObjectProperty 在父 fact 上找这个属性失败就抛 RuleException(R1)。
            fp.setVariableName(sourceExpr.getText());
            fp.setFromSource("stream");

            fromParts.add(fp);
        }

        /**
         * V5.52.2:DRL {@code $xs : List() from collect(InnerPattern)} 形态。
         *
         * <p>grammar alt 1 (LhsCollect 2 alt 中第一个) = {@code drlPattern FROM COLLECT(lhsPattern)};
         * 本 sprint **只接 alt 1**。alt 2 = {@code COLLECT(lhsPattern)} 无 binding — deferred。
         *
         * <p>流程:
         * <ol>
         *   <li>外层 drlPattern binding type 走 resolver 校验</li>
         *   <li>内层 lhsPattern 走 walk() 抽出 List&lt;PropertyCriteria&gt;</li>
         *   <li>包成 MultiCondition(type=AND, conditions=...)挂 FromLeftPart.multiCondition</li>
         *   <li>FromLeftPart.fromSource="collect",property 走 V5.51.3 sum 语义</li>
         * </ol>
         */
        private void handleLhsCollect(DrlParser.LhsCollectContext ctx) {
            DrlParser.DrlPatternContext outer = ctx.drlPattern();
            DrlParser.LhsPatternContext innerPattern = ctx.lhsPattern();
            if (outer == null || innerPattern == null) {
                // alt 2 (无 binding) — 本 sprint 不接
                throw new DrlParseException("V5.52.2 only supports 'drlPattern FROM COLLECT(lhsPattern)' "
                    + "alt for LhsCollect,line " + ctx.getStart().getLine());
            }

            // 1. 外层 type 校验
            String outerType = outer.UPPER_IDENTIFIER().getText();
            if (!resolver.isKnown(outerType)) {
                throw new DrlParseException("from collect 外层 type '" + outerType
                    + "' 未注册,line " + outer.getStart().getLine());
            }

            // 2. 抓 inner PropertyCriteria — 临时用本 visitor 抓后清空 result 列表避免污染
            int savedSize = result.size();
            walk(innerPattern);
            List<PropertyCriteria> innerPcs = new ArrayList<>(result.subList(savedSize, result.size()));
            // 清掉 inner 抓出来的(它们是 inner pattern 的,不是 LHS 顶层 criteria)
            result.subList(savedSize, result.size()).clear();

            // 3. 装 MultiCondition
            MultiCondition mc = new MultiCondition();
            mc.setType(JunctionType.and);
            mc.setConditions(innerPcs);

            // 4. FromLeftPart
            FromLeftPart fp = new FromLeftPart();
            fp.setVariableCategory(outerType);
            // variableName 留 null(runtime 在 collect 分支不读 variableName,走 multiCondition 路径)
            fp.setMultiCondition(mc);
            fp.setFromSource("collect");
            fromParts.add(fp);
        }

        /**
         * V5.52.3:DRL {@code $n : Number() from accumulate(InnerPattern, init(count := 0),
         * action(...), result(count))} 形态。
         *
         * <p>grammar alt 1 = {@code drlPattern FROM ACCUMULATE(lhsPattern, accumulateInit,
         * accumulateAction, accumulateResult)}。本 sprint **只接 alt 1** + **只接 5 内置
         * count/sum/avg/min/max**(从 accumulateResult.expr 的 atom.DRL_COUNT/DRL_SUM/
         * DRL_AVG/DRL_MIN/DRL_MAX 检测)。自定义 result 形态
         * ({@code result(total)} with {@code init(int total := 0)}) deferred 到 V5.53+ —
         * V5.51.3 FromLeftPart.evaluateAccumulate 不接自由 init/action 求值。
         *
         * <p>property 字段:
         * <ul>
         *   <li>count:property=null(runtime 走 match 计数)</li>
         *   <li>sum/avg/min/max:property 暂不抓(init/result 都是同 identifier,
         *       实际 property 在 inner pattern 上)— runtime 用 inner pattern 的
         *       computeValue 拿 match 列表后,需要 caller 在 inner pattern 上指定
         *       property;V5.52.3 限制为单字段 pattern(例 {@code Loan(amount > 1000)}),
         *       property 留 null,runtime 走默认"未指定"路径(走 match 计数)— V5.53+ 再补
         *       property extraction(从 inner lhsPattern 第一条 PropertyCriteria 抓)</li>
         * </ul>
         */
        private void handleLhsAccumulate(DrlParser.LhsAccumulateContext ctx) {
            DrlParser.DrlPatternContext outer = ctx.drlPattern();
            DrlParser.LhsPatternContext innerPattern = ctx.lhsPattern();
            DrlParser.AccumulateResultContext resultCtx = ctx.accumulateResult();
            if (outer == null || innerPattern == null || resultCtx == null) {
                // alt 2 (无 binding) — 本 sprint 不接
                throw new DrlParseException("V5.52.3 only supports 'drlPattern FROM ACCUMULATE(...)' "
                    + "alt for LhsAccumulate,line " + ctx.getStart().getLine());
            }

            // 1. 外层 type 校验
            String outerType = outer.UPPER_IDENTIFIER().getText();
            if (!resolver.isKnown(outerType)) {
                throw new DrlParseException("from accumulate 外层 type '" + outerType
                    + "' 未注册,line " + outer.getStart().getLine());
            }

            // 2. 抓 inner PropertyCriteria → MultiCondition
            int savedSize = result.size();
            walk(innerPattern);
            List<PropertyCriteria> innerPcs = new ArrayList<>(result.subList(savedSize, result.size()));
            result.subList(savedSize, result.size()).clear();
            MultiCondition mc = new MultiCondition();
            mc.setType(JunctionType.and);
            mc.setConditions(innerPcs);

            // 3. 解析 accumulateResult.expr 找 DRL_COUNT/DRL_SUM/... → StatisticType
            StatisticType stat = parseAccumulateStatistic(resultCtx.expr());
            if (stat == null) {
                // V5.52.3 R5 防御:自定义 result(init(int total := 0) + result(total) 形态)
                // 走不到 5 内置 — 拒收,V5.53+ 接
                throw new DrlParseException(
                    "V5.52.3 only supports 5 built-in accumulate stats (count/sum/avg/min/max)."
                    + " 自定义 result(init/result 都用 free-form identifier 形态)deferred 到 V5.53+。"
                    + " 当前 result='" + resultCtx.expr().getText() + "',line "
                    + resultCtx.getStart().getLine());
            }

            FromLeftPart fp = new FromLeftPart();
            fp.setVariableCategory(outerType);
            fp.setMultiCondition(mc);
            fp.setFromSource("accumulate");
            fp.setStatisticType(stat);
            // property 留 null — V5.52.3 简化为接 inner pattern 0-field / 1-field 形态
            //   property 留给 runtime 端 multiCondition 里第一条 PropertyCriteria 推断
            //   (V5.53+ 改进:从 inner PropertyCriteria 抓 property)
            fromParts.add(fp);
        }

        /**
         * V5.52.3:从 accumulateResult.expr 抓 DRL_COUNT/DRL_SUM/DRL_AVG/DRL_MIN/DRL_MAX
         * token 决定 StatisticType。expr 顶层是 exprAtom(atom(...))。
         * <ul>
         *   <li>{@code result(count)} → atom.DRL_COUNT → StatisticType.count</li>
         *   <li>{@code result(sum)} → atom.DRL_SUM → StatisticType.sum</li>
         *   <li>...</li>
         *   <li>{@code result(total)} → atom.IDENTIFIER → null(R5,自定义 result)</li>
         * </ul>
         * 返 null 时 caller 抛 DrlParseException。
         */
        private static StatisticType parseAccumulateStatistic(DrlParser.ExprContext expr) {
            if (expr == null) {
                return null;
            }
            // walk exprAtom 找 atom
            for (DrlParser.ExprAtomContext ea : expr.exprAtom()) {
                DrlParser.AtomContext atom = ea.atom();
                if (atom == null) {
                    continue;
                }
                if (atom.DRL_COUNT() != null) {
                    return StatisticType.count;
                }
                if (atom.DRL_SUM() != null) {
                    return StatisticType.sum;
                }
                if (atom.DRL_AVG() != null) {
                    return StatisticType.avg;
                }
                if (atom.DRL_MIN() != null) {
                    return StatisticType.min;
                }
                if (atom.DRL_MAX() != null) {
                    return StatisticType.max;
                }
            }
            return null;
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
                case "memberOf": return Op.MemberOf;        // V5.51.1:Op 扩 2 值
                case "matches": return Op.Match;
                case "contains": return Op.Contain;
                case "soundslike": return Op.SoundsLike;    // V5.51.1:Op 扩 2 值
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
