package com.ruleforge.ir.drl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V5.42.2 — Datatype 解析器(V5.42 D4 决定:不依赖 DRL 'import' 段)。
 *
 * <p>DRL 完整语法允许 {@code import com.ruleforge.model.Applicant},但 V5.42 plan D4 决定
 * 禁用 import(grammar 缺失,会报 token 错)。DatatypeResolver 接管"如何在 DRL 文本里
 * 知道 'Applicant' 是什么 type":
 * <ul>
 *   <li>预加载一个 "declared types" Map(从 V5.42.1 同 package 的 {@code declare} 段,
 *       或 console-ui 显式传入)— 跟 Drools 6 "Working Memory" 概念一致</li>
 *   <li>任何 'Type(...)' 形式的 pattern,visitor 调用 {@link #resolve(String)} 拿 type info</li>
 *   <li>type 没在 declared types 里 → 抛 {@link DrlParseException}("unknown type X,add 'declare' or pre-register")</li>
 * </ul>
 *
 * <p>V5.42.2 范围内:DatatypeResolver 只 hold 一个 Map<String, TypeInfo>,不查 Class.forName
 * (那需要 ClassLoader 跟 .drl 文件的关联,留 V5.42.4 KnowledgeBuilder 阶段)。
 *
 * <p>不变性:register 之后 immutable,所有 V5.42.2 visitor 共享同一 resolver 实例
 * (DrlAstVisitor 构造时注入,跟 RuleForge 老 .xml Builder pattern 一致)。
 *
 * @since 5.42
 */
public class DatatypeResolver {

    /** 已知 type 名称 → 描述(type name + 字段列表,V5.42.4 完整化) */
    private final Map<String, TypeInfo> types;

    public DatatypeResolver() {
        this(new HashMap<>());
    }

    public DatatypeResolver(Map<String, TypeInfo> initialTypes) {
        this.types = new HashMap<>(initialTypes);
    }

    /**
     * 显式注册一个 type — V5.42.2 API surface。
     * 后期 declare 段 / console-ui 推送都走这条。
     */
    public void register(String typeName, TypeInfo info) {
        if (typeName == null || typeName.isEmpty()) {
            throw new IllegalArgumentException("typeName must not be empty");
        }
        types.put(typeName, info);
    }

    /**
     * 解析 type 名 — 给 {@code Applicant(age > 18)} pattern,visitor 调
     * {@code resolve("Applicant")} 拿 type info;不在则抛 DrlParseException。
     */
    public TypeInfo resolve(String typeName) {
        TypeInfo info = types.get(typeName);
        if (info == null) {
            throw new DrlParseException(
                "Unknown DRL type '" + typeName + "'. "
                + "V5.42 D4 决定:Drools 'import' 不支持,需要预先 register 或在同一 .drl 用 'declare' 段."
                + " 已知 types: " + types.keySet());
        }
        return info;
    }

    /**
     * 非 throw 的查询 — V5.42.2 visitor 用这个看 type 是否 known,失败抛自定义错时一并用。
     */
    public boolean isKnown(String typeName) {
        return types.containsKey(typeName);
    }

    /** 已注册 type 数 */
    public int size() {
        return types.size();
    }

    /**
     * V5.42.2 — 简易 type info 容器。
     *
     * <p>字段列表 + isFact 标记:V5.42.2 只放最基础字段,
     * V5.42.4 DrlDeserializer 才会解析到 Rule.lhs.criterion.propertyCriteria。
     */
    public static class TypeInfo {
        private final String name;
        private final List<String> fields;
        private final boolean fact;

        public TypeInfo(String name, List<String> fields, boolean fact) {
            this.name = name;
            this.fields = fields;
            this.fact = fact;
        }

        public static TypeInfo fact(String name, List<String> fields) {
            return new TypeInfo(name, fields, true);
        }

        public static TypeInfo declared(String name, List<String> fields) {
            return new TypeInfo(name, fields, false);
        }

        public String getName() { return name; }
        public List<String> getFields() { return fields; }
        public boolean isFact() { return fact; }
    }
}
