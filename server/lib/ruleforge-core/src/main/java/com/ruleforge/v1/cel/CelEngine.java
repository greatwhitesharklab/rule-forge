package com.ruleforge.v1.cel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import dev.cel.bundle.Cel;
import dev.cel.bundle.CelFactory;
import dev.cel.common.CelAbstractSyntaxTree;
import dev.cel.common.CelValidationResult;
import dev.cel.common.types.CelTypes;
import dev.cel.expr.Type;
import dev.cel.runtime.CelRuntime;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.SchemaField;
import com.ruleforge.v1.ast.V1DataType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V1 CEL 引擎封装(Google cel-java 0.13.0)。CEL 只做**条件表达式**。
 *
 * <p>Schema-aware:CEL checker 要求变量预先声明(带类型)。本引擎按 {@link Schema} 的字段
 * 声明变量,提供真实类型检查(age=NUMBER/score=NUMBER/blacklisted=BOOLEAN/tags=LIST 等)。
 * 一个 Schema → 一个 Cel 实例,按 schema name 缓存(线程安全)。
 *
 * <p>V1DataType → CEL 类型映射:
 * <ul>
 *   <li>NUMBER → DYN(避开 int 字面量 vs double 变量的 overload 摩擦;条件阈值多为整数,
 *       DYN 让 {@code age >= 18} 直接通过,运行时数值统一比较)</li>
 *   <li>STRING → STRING</li>
 *   <li>BOOLEAN → BOOL</li>
 *   <li>LIST → list&lt;dyn&gt;</li>
 * </ul>
 * <p>NUMBER 用 DYN 而非 DOUBLE/INT64 的代价:数值类型检查弱化,但保留更重要的安全边界 —
 * 语法校验、boolean 返回、未声明变量拒绝、CEL 天然禁赋值。MVP 取舍合理。
 *
 * <p>CEL 语法本身无赋值(pure expression),天然满足 design doc Block 3 的"禁赋值/副作用/循环"。
 * 不绑定自定义函数 → 只能用标准 pure 函数。列表成员用 {@code 'vip' in tags}
 * (CEL {@code contains} 是字符串子串函数,非列表成员)。
 */
public final class CelEngine {

    /** schema 签名(name + 字段集)→ 编译好的 Cel。key 含字段集避免同名不同字段的 schema 碰撞。 */
    private static final ConcurrentHashMap<String, Cel> CEL_BY_SCHEMA = new ConcurrentHashMap<>();

    private CelEngine() {
    }

    /** 按 Schema 构建/缓存 Cel(声明全部字段)。Schema 为 null 时返回无声明的 standard Cel。 */
    static Cel celFor(Schema schema) {
        if (schema == null || schema.getName() == null) {
            return CelFactory.standardCelBuilder().build();
        }
        return CEL_BY_SCHEMA.computeIfAbsent(schemaSignature(schema), k -> buildCel(schema));
    }

    /** schema 签名:name + sorted field names(避免同名不同字段的 schema 碰撞缓存)。 */
    private static String schemaSignature(Schema schema) {
        StringBuilder sb = new StringBuilder(schema.getName());
        if (schema.getFields() != null) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (SchemaField f : schema.getFields()) {
                names.add(f.getName() + ":" + f.getType());
            }
            java.util.Collections.sort(names);
            sb.append("[").append(String.join(",", names)).append("]");
        }
        return sb.toString();
    }

    private static Cel buildCel(Schema schema) {
        dev.cel.bundle.CelBuilder builder = CelFactory.standardCelBuilder();
        if (schema.getFields() != null) {
            for (SchemaField field : schema.getFields()) {
                builder.addVar(field.getName(), v1TypeToCel(field.getType()));
            }
        }
        // V7.4:声明库 namespace(DYN 通配,让 CEL checker 过 param.xxx / const.xxx 字段访问;
        // 运行时由会话参数/常量注入 binding)。DYN 选型同 NUMBER 取舍 —— 弱化类型检查换通过率。
        builder.addVar("param", dynType());
        builder.addVar("constant", dynType());
        return builder.build();
    }

    /** V1DataType → CEL protobuf Type。 */
    private static Type v1TypeToCel(V1DataType type) {
        if (type == null) {
            return dynType();
        }
        switch (type) {
            case NUMBER:
                // INT64(非 DYN):CEL 严格无 int↔double 自动转换,统一 int64 让
                // field(int64) op int字面量(int64) overload 匹配。cash loan 数值(年龄/
                // 分数/收入/风险分)都是整数。小数阈值 MVP 不支持(文档注明)。
                return Type.newBuilder().setPrimitive(Type.PrimitiveType.INT64).build();
            case STRING:
                return Type.newBuilder().setPrimitive(Type.PrimitiveType.STRING).build();
            case BOOLEAN:
                return Type.newBuilder().setPrimitive(Type.PrimitiveType.BOOL).build();
            case LIST:
                return Type.newBuilder()
                        .setListType(Type.ListType.newBuilder().setElemType(dynType()))
                        .build();
            default:
                return dynType();
        }
    }

    private static Type dynType() {
        return Type.newBuilder().setDyn(com.google.protobuf.Empty.getDefaultInstance()).build();
    }

    /**
     * 编译 + 校验 CEL 表达式必须返回 boolean(按 schema 声明的变量类型检查)。
     *
     * @throws CelConditionException 语法错 / 未声明变量 / 类型错 / 非 boolean 返回
     */
    public static CelAbstractSyntaxTree compileBoolean(String expr, Schema schema) {
        Cel cel = celFor(schema);
        CelValidationResult result;
        try {
            result = cel.compile(expr);
        } catch (Exception e) {
            throw new CelConditionException("CEL 编译失败: " + expr + " — " + e.getMessage(), e);
        }
        if (result.hasError()) {
            throw new CelConditionException("CEL 校验失败: " + expr + " — " + result.getErrorString());
        }
        CelAbstractSyntaxTree ast;
        try {
            ast = result.getAst();
        } catch (dev.cel.common.CelValidationException e) {
            throw new CelConditionException("CEL 校验失败: " + expr + " — " + e.getMessage(), e);
        }
        String resultType = CelTypes.format(ast.getResultType());
        if (!"bool".equals(resultType)) {
            throw new CelConditionException(
                    "CEL condition 必须返回 boolean,实际返回 " + resultType + ": " + expr);
        }
        return ast;
    }

    /**
     * 求 CEL boolean 表达式值(按 schema 变量类型)。NUMBER 用 DYN。
     * bindings 里数值 coerce:Integer/Short/Byte → Long,Float → Double
     * (CEL int64=Long,double=Double;Java Integer 直接传会 less_int64 overload 失配)。
     */
    public static boolean evalBoolean(String expr, Map<String, Object> bindings, Schema schema) {
        try {
            Cel cel = celFor(schema);
            CelAbstractSyntaxTree ast = compileBoolean(expr, schema);
            CelRuntime.Program program = cel.createProgram(ast);
            Object result = program.eval(coerceNumericBindings(bindings));
            if (!(result instanceof Boolean)) {
                throw new CelConditionException("CEL 求值非 boolean: " + expr + " → " + result);
            }
            return (Boolean) result;
        } catch (CelConditionException e) {
            throw e;
        } catch (Exception e) {
            throw new CelConditionException("CEL 求值失败: " + expr + " — " + e.getMessage(), e);
        }
    }

    /** bindings 数值 coerce:所有 Number→Long(NUMBER 声明 INT64,CEL int64=Long)。
     *  Double 也截断成 Long — cash loan 数值都是整数;小数见 v1TypeToCel 注。 */
    private static Map<String, ?> coerceNumericBindings(Map<String, Object> bindings) {
        if (bindings == null) {
            return ImmutableMap.of();
        }
        Map<String, Object> coerced = new HashMap<>();
        for (Map.Entry<String, Object> e : bindings.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Number && !(v instanceof Long)) {
                coerced.put(e.getKey(), ((Number) v).longValue());
            } else {
                coerced.put(e.getKey(), v);
            }
        }
        return coerced;
    }

    /** POJO fact → CEL bindings Map(Jackson convertValue)。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toBindings(Object fact) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(fact, Map.class);
    }
}
