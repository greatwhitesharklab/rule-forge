package com.ruleforge.console.repository.model;

public enum FileType {
    Ruleset, RulesetLib, UL, DecisionTable, ScriptDecisionTable, Crosstab,
    RuleFlow, DecisionTree, Scorecard, ComplexScorecard,
    VariableLibrary, ParameterLibrary, ConstantLibrary, ActionLibrary,
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
