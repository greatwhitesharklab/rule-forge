package com.ruleforge.builder;

import com.ruleforge.model.library.ResourceLibrary;
import com.ruleforge.model.library.variable.Variable;
import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.model.rete.Rete;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgePackageImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KnowledgeBase {
    private static final String PARAM_CATEGORY = "参数";

    private ResourceLibrary resourceLibrary;
    private Rete rete;
    private KnowledgePackageImpl knowledgePackage;

    public KnowledgeBase(Rete rete) {
        this.rete = rete;
        this.resourceLibrary = rete.getResourceLibrary();
    }

    public KnowledgePackage getKnowledgePackage() {
        if (this.knowledgePackage != null) {
            return this.knowledgePackage;
        }

        this.knowledgePackage = new KnowledgePackageImpl();
        this.knowledgePackage.setRete(this.rete);
        Map<String, String> variableCategoryMap = new HashMap<>();
        this.knowledgePackage.setVariableCategoryMap(variableCategoryMap);
        List<VariableCategory> variableCategories = this.resourceLibrary.getVariableCategories();
        Map<String, String> parameters = new HashMap<>();
        this.knowledgePackage.setParameters(parameters);

        // V6.3 — 3-level nested do-while find-first → enhanced for + 2 个 continue。
        // 原来 3-level do-while: 内层找 name=="参数", 中层过滤 null variables,
        // 外层过滤 empty variables, 内嵌 for 收集。实际行为是 "process all
        // matching items" — 等价 enhanced for + 2 个 continue (filter non-matching
        // + process matching)。迭代顺序由 List iterator 状态决定, 两种写法一致。
        // 行为 100% 等价 (锁定 [[v633-knowledgebase-dowhile-flatten]]):
        // - 所有 category 都 put 进 variableCategoryMap (在 name check 之前,跟原顺序一致)
        // - name == "参数" + variables != null + !variables.isEmpty() 才贡献 parameters
        // - iterator 状态一致 (List 顺序遍历)
        for (VariableCategory category : variableCategories) {
            String name = category.getName();
            variableCategoryMap.put(name, category.getClazz());
            if (!PARAM_CATEGORY.equals(name)) {
                continue;
            }
            List<Variable> variables = category.getVariables();
            if (variables == null || variables.isEmpty()) {
                continue;
            }
            for (Variable var : variables) {
                parameters.put(var.getName(), var.getType().name());
            }
        }

        return this.knowledgePackage;
    }

    public Rete getRete() {
        return this.rete;
    }

    public ResourceLibrary getResourceLibrary() {
        return this.resourceLibrary;
    }
}
