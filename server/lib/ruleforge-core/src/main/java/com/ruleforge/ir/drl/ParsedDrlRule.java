package com.ruleforge.ir.drl;

import lombok.Data;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * V5.42.2 — DrlAstVisitor 输出中间 DTO。
 *
 * <p>Antlr ParseTree 走 visitor 后,产出 {@link ParsedDrlRule},只装顶层 rule metadata
 * (name / salience / agendaGroup 等 11 attribute)+ lhs/rhs ParseTree 引用(留 V5.42.4
 * DrlDeserializer 转 Rule model)。
 *
 * <p>V5.42.2 阶段不构建 Lhs/Rhs model 对象 — 那是 V5.42.4 的工作。本类只承担
 * "visitor 跑通 + 顶层 metadata 正确解析"的 sanity check。
 *
 * <p>equals / hashCode 不实现 — 测试中按字段手动断言。
 *
 * @since 5.42
 */
@Data
public class ParsedDrlRule {

    /** 顶层 DRL rule name(rule "X" when ...) */
    private String name;

    /** D2 决定:extends 的父 rule name,null = 不 extends */
    private String extendsName;

    /** 11 个顶层 attribute,key = DRL attribute 名(小写),value = 解析值 */
    private List<DrlAttribute> attributes = new ArrayList<>();

    /** when 段 ParseTree(V5.42.4 DrlDeserializer 才会走) */
    private ParseTree lhsParseTree;

    /** then 段 ParseTree(同上) */
    private ParseTree rhsParseTree;

    /**
     * 给一个 attribute — V5.42.2 visitor 在 ruleAttributes 内逐个加进来。
     * value 是 String(顶层 attribute 全部接受 String/number/boolean 形式,grammar 强类型不在
     * 本层做,留 V5.42.2 DatatypeResolver 校验)。
     */
    public void addAttribute(String name, String value) {
        attributes.add(new DrlAttribute(name, value));
    }

    /** 一条顶层 attribute 记录 — key + value。 */
    @Data
    public static class DrlAttribute {
        private final String name;
        private final String value;
    }
}
