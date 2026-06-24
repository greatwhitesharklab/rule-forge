package com.ruleforge.v1.exec;

import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.model.library.Datatype;
import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Act;
import com.ruleforge.model.library.variable.CategoryType;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.library.variable.VariableLibrary;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.model.rete.builder.ReteBuilder;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.v1.ast.Schema;
import com.ruleforge.v1.ast.SchemaField;
import com.ruleforge.v1.ast.V1DataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * V1 RETE 知识包构建器(W2 共享基建)。
 *
 * <p>把 V1 schema + 一组 RETE Rule 编成 {@link KnowledgePackage},供 RuleSet/DecisionTable
 * 编译器共用。fact 模型 = GeneralEntity(Map-backed),category = schema name。
 *
 * <p>VariableLibrary 从 schema 字段生成:每个 SchemaField → Variable(Act.In,可读)。
 * score/decision/flags 这类被 action 写的字段也声明 Act.InOut(可读可写)。
 */
public final class V1KnowledgeBuilder {

    private V1KnowledgeBuilder() {
    }

    /** schema + rules → KnowledgePackage(buildRete)。 */
    public static KnowledgePackage build(Schema schema, List<Rule> rules) {
        VariableLibrary lib = buildVariableLibrary(schema);
        ResourceLibrary rl = new ResourceLibrary(Collections.singletonList(lib), new ArrayList<>(), new ArrayList<>());
        Rete rete = new ReteBuilder().buildRete(rules, rl);
        return new KnowledgeBase(rete).getKnowledgePackage();
    }

    /** schema → VariableLibrary(单 category = schema name,GeneralEntity fact 模型)。 */
    public static VariableLibrary buildVariableLibrary(Schema schema) {
        VariableLibrary lib = new VariableLibrary();
        VariableCategory cat = new VariableCategory();
        cat.setName(schema.getName());
        // GeneralEntity fact:clazz = category 名(=targetClass),CategoryType.Clazz
        cat.setType(CategoryType.Clazz);
        cat.setClazz(schema.getName());
        List<Variable> variables = new ArrayList<>();
        if (schema.getFields() != null) {
            for (SchemaField f : schema.getFields()) {
                Variable v = new Variable();
                v.setName(f.getName());
                v.setLabel(f.getLabel() != null ? f.getLabel() : f.getName());
                v.setType(v1ToDatatype(f.getType()));
                v.setAct(Act.InOut); // V1 fact 字段可读可写(action 可能写)
                variables.add(v);
            }
        }
        cat.setVariables(variables);
        lib.addVariableCategory(cat);
        return lib;
    }

    private static Datatype v1ToDatatype(V1DataType type) {
        if (type == null) return Datatype.Object;
        switch (type) {
            case NUMBER: return Datatype.Double;
            case STRING: return Datatype.String;
            case BOOLEAN: return Datatype.Boolean;
            case LIST: return Datatype.List;
            default: return Datatype.Object;
        }
    }
}
