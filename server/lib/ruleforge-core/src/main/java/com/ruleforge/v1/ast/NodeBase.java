package com.ruleforge.v1.ast;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * V1 统一 AST 节点基类。所有节点继承它 — 未来 Excel/AI/DMN/旧 DSL 导入全部转成同一个 AST。
 *
 * <p>Jackson 多态:JSON 的 {@code "type"} 字段决定反序列化成哪个子类
 * ({@link JsonTypeInfo.Id#NAME} + {@link JsonTypeInfo.As#EXISTING_PROPERTY})。
 * 子类 {@link #getType()} 返回常量,既是序列化输出也是反序列化路由 key。
 *
 * <p>不参考 urule AST(XML 序列化、老 DSL、无统一基类)。借 urule 的:
 * RuleSet 三件套、DecisionTable 结构、ScoreCard items/bands。
 *
 * @see RuleSetNode / DecisionTableNode / ScoreCardNode / StartNode / DecisionNode
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StartNode.class, name = "Start"),
        @JsonSubTypes.Type(value = RuleSetNode.class, name = "RuleSet"),
        @JsonSubTypes.Type(value = DecisionTableNode.class, name = "DecisionTable"),
        @JsonSubTypes.Type(value = ScoreCardNode.class, name = "ScoreCard"),
        @JsonSubTypes.Type(value = DecisionNode.class, name = "Decision"),
})
public abstract class NodeBase {
    private String id;
    private String name;
    private String description;
    /** V7.5:规则文件引用(RuleSet/DecisionTable/ScoreCard 节点指向独立规则文件,
     *  如 "precheck" → V1规则集/precheck.v1rs.json)。设了执行时从 ruleFiles 加载规则内容,
     *  不内嵌 rules/inputs/outputs/cards(跨流程复用 + 独立 git diff)。Start/Decision 不用。 */
    private String ruleRef;

    /** Jackson discriminator 常量,子类返回自己的 NodeType 名。 */
    public abstract String getType();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRuleRef() {
        return ruleRef;
    }

    public void setRuleRef(String ruleRef) {
        this.ruleRef = ruleRef;
    }
}
