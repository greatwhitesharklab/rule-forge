package com.ruleforge.v1.compile;

import dev.cel.common.ast.CelExpr;
import dev.cel.common.ast.CelExpr.ExprKind.Kind;
import dev.cel.common.ast.CelConstant;
import com.ruleforge.model.rule.Op;
import com.ruleforge.model.rule.ParameterValue;
import com.ruleforge.model.rule.SimpleValue;
import com.ruleforge.model.rule.lhs.And;
import com.ruleforge.model.rule.lhs.Criteria;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Left;
import com.ruleforge.model.rule.lhs.LeftType;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.model.rule.lhs.Or;
import com.ruleforge.model.rule.lhs.VariableLeftPart;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.cel.CelConditionException;
import com.ruleforge.v1.cel.CelEngine;
import dev.cel.common.CelAbstractSyntaxTree;

import java.util.HashMap;
import java.util.Map;

/**
 * V1 CEL 条件 → 老 RETE criteria 树翻译器(W1-3 critical path)。
 *
 * <p>把 CEL boolean 表达式翻译成 ruleforge-core 的 {@link Criteria}/{@link And}/{@link Or} 树,
 * 喂 ReteBuilder.buildRete 编进 RETE alpha/beta 网络(真正的 RETE 性能,非 opaque CEL eval)。
 *
 * <p>MVP 支持的 CEL 子集(覆盖现金贷条件:准入/定价/评分卡 band):
 * <ul>
 *   <li>比较:字段 op 字面量 — {@code age >= 18} / {@code blacklisted == true} / {@code riskScore < 30}</li>
 *   <li>逻辑 AND: {@code a > 1 && b < 2} → And junction</li>
 *   <li>逻辑 OR: {@code a > 1 || b < 2} → Or junction</li>
 *   <li>布尔字段裸引用: {@code blacklisted} → blacklisted == true</li>
 *   <li>布尔字段取反: {@code !blacklisted} → blacklisted == false</li>
 * </ul>
 *
 * <p>不支持的 CEL 构造(算术 on field / 嵌套 NOT / 函数调用 / 列表 in)→ 抛
 * {@link CelConditionException},提示用 CEL-eval 路径(ScoreCard 等)或简化表达式。
 * 现金贷条件 99% 是字段-字面量比较,子集够用。
 *
 * <p>fact 模型:category = {@link Schema#getName()};字段名 = CEL ident。
 * {@link VariableLeftPart}(category, name, datatype) 跟 V1ReteSmokeTest 确认的模式一致。
 */
public final class CelCriteriaTranslator {

    private CelCriteriaTranslator() {
    }

    /** CEL 表达式 → Criterion(可能 Criteria atom 或 And/Or junction)。 */
    public static Criterion translate(String celExpr, Schema schema) {
        CelAbstractSyntaxTree ast = CelEngine.compileBoolean(celExpr, schema);
        return translateExpr(ast.getExpr(), schema);
    }

    /** 包装成 Lhs(规则组装用)。 */
    public static Lhs translateToLhs(String celExpr, Schema schema) {
        Lhs lhs = new Lhs();
        lhs.setCriterion(translate(celExpr, schema));
        return lhs;
    }

    private static Criterion translateExpr(CelExpr expr, Schema schema) {
        Kind kind = expr.getKind();
        switch (kind) {
            case CALL:
                return translateCall(expr, schema);
            case IDENT:
                // 裸布尔字段 → field == true
                return criteriaForBooleanField(expr.ident().name(), true, schema);
            case CONSTANT:
                // 裸常量(如 true/false)→ 仅支持 boolean: true→恒真占位(用 1==1 风格不支持,抛错让用户写明确条件)
                throw new CelConditionException(
                        "CEL 裸常量不支持作为 RETE 条件,请写明确的字段比较: " + describe(expr));
            default:
                throw new CelConditionException(
                        "CEL 节点类型 " + kind + " 不支持在 RETE 条件中(只支持 比较/&&/||/布尔字段/!字段): "
                                + describe(expr));
        }
    }

    private static Criterion translateCall(CelExpr expr, Schema schema) {
        String fn = expr.call().function();
        java.util.List<CelExpr> args = expr.call().args();
        switch (fn) {
            // 比较运算:字段 op 字面量
            case "_==_":
            case "_!=_":
            case "_<_":
            case "_<=_":
            case "_>_":
            case "_>=_":
                return translateComparison(fn, args.get(0), args.get(1), schema);
            // 逻辑 AND
            case "_&&_": {
                And and = new And();
                for (CelExpr arg : args) {
                    and.addCriterion(translateExpr(arg, schema));
                }
                return and;
            }
            // 逻辑 OR
            case "_||_": {
                Or or = new Or();
                for (CelExpr arg : args) {
                    or.addCriterion(translateExpr(arg, schema));
                }
                return or;
            }
            // 逻辑 NOT:仅支持 !booleanField(→ field==false),不支持嵌套 !(...)
            // CEL 一元 not 函数名是 "!_"(单操作数)
            case "!_": {
                CelExpr operand = args.get(0);
                if (operand.getKind() == Kind.IDENT) {
                    return criteriaForBooleanField(operand.ident().name(), false, schema);
                }
                throw new CelConditionException(
                        "CEL 嵌套 NOT !(...) 不支持在 RETE 条件中,请展开为显式比较: !" + describe(operand));
            }
            default:
                throw new CelConditionException(
                        "CEL 函数/运算 '" + fn + "' 不支持在 RETE 条件中"
                                + "(只支持 比较/&&/||/布尔字段/!字段;算术 on field / 函数调用 用 CEL-eval 路径): "
                                + describe(expr));
        }
    }

    /** 翻译字段 op 字面量/param.xxx 比较。左=字段(ident),右=字面量(constant)或 param.xxx(SELECT)。 */
    private static Criterion translateComparison(String op, CelExpr leftExpr, CelExpr rightExpr, Schema schema) {
        // 支持字段在左 或 在右(翻转运算符)
        boolean fieldOnLeft = leftExpr.getKind() == Kind.IDENT;
        boolean fieldOnRight = rightExpr.getKind() == Kind.IDENT;
        if (fieldOnLeft && rightExpr.getKind() == Kind.CONSTANT) {
            return buildCriteria(leftExpr.ident().name(), mapOp(op), rightExpr.constant(), schema);
        }
        if (fieldOnRight && leftExpr.getKind() == Kind.CONSTANT) {
            // 字段在右:翻转运算符(18 <= age → age >= 18)
            return buildCriteria(rightExpr.ident().name(), flipOp(mapOp(op)), leftExpr.constant(), schema);
        }
        // V7.4:字段 op param.xxx(动态右值 — ParameterValue 走会话参数通道,非 fact 字段)
        if (fieldOnLeft && rightExpr.getKind() == Kind.SELECT) {
            String paramField = selectParamField(rightExpr);
            if (paramField != null) {
                return buildCriteriaWithParam(leftExpr.ident().name(), mapOp(op), paramField, schema);
            }
        }
        if (fieldOnRight && leftExpr.getKind() == Kind.SELECT) {
            String paramField = selectParamField(leftExpr);
            if (paramField != null) {
                return buildCriteriaWithParam(rightExpr.ident().name(), flipOp(mapOp(op)), paramField, schema);
            }
        }
        throw new CelConditionException(
                "CEL 比较必须 字段 op 字面量/param.xxx(如 age >= 18 或 riskScore >= param.threshold): "
                        + describe(leftExpr) + " " + op + " " + describe(rightExpr));
    }

    /** SELECT expr 是 param.{field}?返 field 名,否则 null(仅 pl 参数库动态右值;cl/vl 留后续)。 */
    private static String selectParamField(CelExpr selectExpr) {
        CelExpr operand = selectExpr.select().operand();
        if (operand.getKind() == Kind.IDENT && "param".equals(operand.ident().name())) {
            return selectExpr.select().field();
        }
        return null;
    }

    /** 构造 Criteria(field op param.{paramName}),右值 ParameterValue(运行时从会话参数取)。 */
    private static Criteria buildCriteriaWithParam(String fieldName, Op op, String paramName, Schema schema) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(schema.getName());
        part.setVariableName(fieldName);
        part.setVariableLabel(fieldName);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(op);
        ParameterValue pv = new ParameterValue();
        pv.setVariableName(paramName);
        pv.setVariableLabel(paramName);
        c.setValue(pv);
        return c;
    }

    /** 构造 Criteria(field, op, literalValue)。datatype 不设(留 null)→ Criteria.evaluate
     * 运行时从 fact 实际值推断(Utils.getDatatype),兼容 int/double/boolean 等任意 fact 类型。 */
    private static Criteria buildCriteria(String fieldName, Op op, CelConstant literal, Schema schema) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(schema.getName());
        part.setVariableName(fieldName);
        part.setVariableLabel(fieldName);
        // datatype 留 null:Criteria.evaluate 若 VariableLeftPart.datatype==null,
        // 走 Utils.getDatatype(leftResult) 从 fact 实际值推断(int→Integer/double→Double)
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(op);
        SimpleValue sv = new SimpleValue();
        sv.setContent(literalToContent(literal));
        c.setValue(sv);
        return c;
    }

    /** 布尔字段 → Criteria(field == bool)。datatype 留 null 走推断。 */
    private static Criteria criteriaForBooleanField(String fieldName, boolean value, Schema schema) {
        Criteria c = new Criteria();
        Left left = new Left();
        left.setType(LeftType.variable);
        VariableLeftPart part = new VariableLeftPart();
        part.setVariableCategory(schema.getName());
        part.setVariableName(fieldName);
        part.setVariableLabel(fieldName);
        left.setLeftPart(part);
        c.setLeft(left);
        c.setOp(Op.Equals);
        SimpleValue sv = new SimpleValue();
        sv.setContent(value ? "true" : "false");
        c.setValue(sv);
        return c;
    }

    /** CEL 比较运算符 → ruleforge Op。 */
    private static Op mapOp(String celOp) {
        switch (celOp) {
            case "_==_": return Op.Equals;
            case "_!=_": return Op.NotEquals;
            case "_<_": return Op.LessThen;
            case "_<=_": return Op.LessThenEquals;
            case "_>_": return Op.GreaterThen;
            case "_>=_": return Op.GreaterThenEquals;
            default: throw new CelConditionException("未知 CEL 比较运算符: " + celOp);
        }
    }

    /** 翻转运算符(字段在右时)。 */
    private static Op flipOp(Op op) {
        switch (op) {
            case LessThen: return Op.GreaterThen;
            case LessThenEquals: return Op.GreaterThenEquals;
            case GreaterThen: return Op.LessThen;
            case GreaterThenEquals: return Op.LessThenEquals;
            default: return op; // == / != 对称
        }
    }

    /** CelConstant 字面量 → SimpleValue content 字符串。 */
    private static String literalToContent(CelConstant literal) {
        CelConstant.Kind kind = literal.getKind();
        switch (kind) {
            case BOOLEAN_VALUE: return String.valueOf(literal.booleanValue());
            case INT64_VALUE: return String.valueOf(literal.int64Value());
            case DOUBLE_VALUE: return String.valueOf(literal.doubleValue());
            case STRING_VALUE: return literal.stringValue();
            case NULL_VALUE: return "";
            default: throw new CelConditionException("CEL 字面量类型 " + kind + " 不支持: " + literal);
        }
    }

    /** 调试用:CEL expr 简述。 */
    private static String describe(CelExpr expr) {
        try {
            Kind k = expr.getKind();
            if (k == Kind.IDENT) return "字段(" + expr.ident().name() + ")";
            if (k == Kind.CONSTANT) return "字面量(" + literalToContent(expr.constant()) + ")";
            if (k == Kind.CALL) return "调用(" + expr.call().function() + ")";
            return String.valueOf(k);
        } catch (Exception e) {
            return "<describe 失败>";
        }
    }
}
