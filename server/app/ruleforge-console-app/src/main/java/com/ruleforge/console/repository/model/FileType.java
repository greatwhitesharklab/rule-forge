package com.ruleforge.console.repository.model;

public enum FileType {
    Ruleset, RulesetLib, UL, DecisionTable, ScriptDecisionTable, Crosstab,
    RuleFlow, DecisionTree, Scorecard, ComplexScorecard, Drl,
    VariableLibrary, ParameterLibrary, ConstantLibrary, ActionLibrary,
    // V6.20.0 P3:DMN/PMML 标准决策模型(只读/导入,不通过 UI 编辑)
    Dmn, Pmml,
    DIR, Package;

    public static FileType parse(String type) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        try {
            return valueOf(type);
        } catch (IllegalArgumentException e) {
            for (FileType ft : values()) {
                if (ft.name().equalsIgnoreCase(type)) {
                    return ft;
                }
            }
            return null;
        }
    }
}
