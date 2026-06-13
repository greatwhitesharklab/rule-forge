package com.ruleforge.ir.drl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V5.42.2 / V5.44.3 — Datatype 解析器。
 *
 * <p>负责 DRL 文本里 {@code Type(...)} 形式的 type 解析。
 *
 * <p><b>V5.42.2 初版</b>:不依赖 DRL 'import' 段。type 通过同 package {@code declare}
 * 段或 console-ui 显式 register 进入,visitor 调 {@link #resolve(String)} 查表。
 *
 * <p><b>V5.44.3</b>:library 资源从老 .xml 改 DRL 顶层 {@code import} 段(grammar 新增
 * DRL_IMPORT 关键字,见 DrlLexer.g4)。本类在 V5.44.3 加 imports 列表:
 * <ul>
 *   <li>{@link #addImport(String)} — 累加 DRL 顶层 import 段收集到的 library 路径</li>
 *   <li>{@link #resolve(String)} — 先查 builtin types,再查 import 列表("library 路径
 *       → 可能含 type alias" 暂不展开,仅作为 import 列表的 placeholder,V5.45+ 跟进
 *       library 实际加载 + type alias 抽取)</li>
 *   <li>若 type 仍未知,fallback 到 library import 列表("library path 内的 type 名
 *       通过 library 文件加载抽出来,记成 alias" — V5.44.3 仅留位置,实现 V5.45+ 跟进)</li>
 * </ul>
 *
 * <p>V5.44.3 设计取舍:library 实际**加载**未在 V5.44.3 实现。V5.44.3 只把 import
 * 段**收集**进 DatatypeResolver,resolve() 顺序改"builtin → imports 列表 →
 * throw"即可。library 文件加载留 V5.45+ 单独 PR。
 *
 * <p>不变性:register / addImport 之后内部状态 immutable(对调用方),所有 V5.42.2
 * visitor 共享同一 resolver 实例(DrlAstVisitor 构造时注入,跟 RuleForge 老 .xml
 * Builder pattern 一致)。
 *
 * @since 5.42
 */
public class DatatypeResolver {

    /** 已知 type 名称 → 描述(type name + 字段列表,V5.42.4 完整化) */
    private final Map<String, TypeInfo> types;

    /**
     * V5.44.3 — 顶层 import 段收集到的 library 路径列表。按 .drl 出现顺序追加,
     * 重复 import 路径去重(LinkedHashSet 保留插入顺序)。
     */
    private final Set<String> imports = new LinkedHashSet<>();

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
     * V5.44.3 — 顶层 import 段收集到的 library 路径。DrlAstVisitor 在 visit 完
     * importStatement 列表后调 {@code resolver.addImport(path)} 把每条路径塞进
     * imports 列表(LinkedHashSet 自动去重)。
     *
     * <p>V5.44.3 不解析 library 文件内容(那需要文件 IO + library grammar,留
     * V5.45+ 跟进)。本方法仅记路径,resolve() 时把"未知 type → import 列表里有
     * 路径"作为 fallback hint 返给 caller。
     *
     * <p>注意:V5.44.3 不会因为 caller 没调这个方法就改行为(DRL 文件没 import
     * 段时,imports 列表是空,跟 V5.42.2 行为一致)。
     */
    public void addImport(String libraryPath) {
        if (libraryPath == null || libraryPath.isEmpty()) {
            throw new IllegalArgumentException("libraryPath must not be empty");
        }
        imports.add(libraryPath);
    }

    /**
     * V5.44.3 — 拿全部 import 路径(不可变 snapshot,按 DRL 出现顺序)。
     * V5.45+ library 加载器会调这个拿路径列表去并发 fetch。
     */
    public List<String> getImports() {
        return Collections.unmodifiableList(new ArrayList<>(imports));
    }

    /**
     * 解析 type 名 — 给 {@code Applicant(age > 18)} pattern,visitor 调
     * {@code resolve("Applicant")} 拿 type info;不在则抛 DrlParseException。
     *
     * <p>V5.44.3:resolve 顺序:builtin types → import 列表(若 path 命中某个
     * library,留 V5.45+ 抽出 type alias;V5.44.3 不展开)。仍然 throw 但错误
     * 信息会附 import 列表便于诊断。
     */
    public TypeInfo resolve(String typeName) {
        TypeInfo info = types.get(typeName);
        if (info != null) {
            return info;
        }
        // V5.44.3 — type 不在 builtin:看 import 列表是否有匹配 library
        // (V5.44.3 不做 library 实际加载,仅作为 hint)
        if (!imports.isEmpty()) {
            throw new DrlParseException(
                "Unknown DRL type '" + typeName + "'. "
                + "V5.44.3:已 declared import 列表 " + imports + " 但本 type 不在 builtin。"
                + "V5.45+ 跟进 library 文件实际加载,届时 type 可解析。");
        }
        throw new DrlParseException(
            "Unknown DRL type '" + typeName + "'. "
            + "V5.44.3:DRL 'import' 段支持 library 路径(需 'import \"libs/x.drl\";'),"
            + "或在同一 .drl 用 'declare' 段. 已知 types: " + types.keySet());
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
     *
     * <p><b>V5.45.1</b>:加 {@code extendsName} + {@code annotations} 字段:
     * <ul>
     *   <li>{@code extendsName} — DRL 顶层 {@code declare X extends Y} 段抽出,
     *       V5.45.2 library loader 用作"resolve 顺序 builtin → imports → extends 链"
     *       的入口(实际 V5.45.2 还没消费,V5.45.1 先建字段)</li>
     *   <li>{@code annotations} — DRL 顶层 {@code @role(event)} / {@code @timestamp("created")}
     *       段抽出,key=annotation 名,value=annotation 整体形参文本
     *       (V5.45.1 简化:不拆 {@code key=val} 形式,整体当 string)</li>
     * </ul>
     */
    public static class TypeInfo {
        private final String name;
        private final List<String> fields;
        private final boolean fact;
        /** V5.45.1 — declare X extends Y 的 Y;null 表示无 extends */
        private final String extendsName;
        /** V5.45.1 — declare 段 annotation 列表;key=annotation 名,value=形参文本(简化) */
        private final Map<String, String> annotations;

        public TypeInfo(String name, List<String> fields, boolean fact) {
            this(name, fields, fact, null, java.util.Collections.emptyMap());
        }

        /**
         * V5.45.1 — 完整构造器(extendsName + annotations)。V5.45.1+ caller 走这条;
         * V5.42.2 老 caller 走 3 参版本默认 null + empty map,行为兼容。
         */
        public TypeInfo(String name, List<String> fields, boolean fact,
                        String extendsName, Map<String, String> annotations) {
            this.name = name;
            this.fields = fields;
            this.fact = fact;
            this.extendsName = extendsName;
            this.annotations = annotations == null
                ? java.util.Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(annotations));
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

        /** V5.45.1 — declare 段 extends 名;null 表示无 extends */
        public String getExtendsName() { return extendsName; }

        /** V5.45.1 — declare 段 annotation 列表(key=annotation 名,value=形参文本);never null */
        public Map<String, String> getAnnotations() { return annotations; }
    }
}
