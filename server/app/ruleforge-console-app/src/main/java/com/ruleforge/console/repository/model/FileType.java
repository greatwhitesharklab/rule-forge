package com.ruleforge.console.repository.model;

public enum FileType {
    Ruleset, RulesetLib, UL, DecisionTable, ScriptDecisionTable, Crosstab,
    RuleFlow, DecisionTree, Scorecard, ComplexScorecard, Drl,
    VariableLibrary, ParameterLibrary, ConstantLibrary, ActionLibrary,
    // V6.20.0 P3:DMN/PMML 标准决策模型(只读/导入,不通过 UI 编辑)
    Dmn, Pmml,
    // V7.0.0:V1 决策流(.json,React Flow 画布资产,后端 V1FlowRunner 可执行)
    V1Flow,
    // V7.4:V1 库(.v1lib.json,vl/cl/pl 四库,pl/cl 动态右值)
    V1Library,
    // V7.5:V1 规则独立文件(.v1rs.json/.v1dt.json/.v1sc.json,决策流引用)
    V1RuleSet, V1DecisionTable, V1ScoreCard,
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
