package com.ruleforge.console.repository.model;

public enum Type {
    root, project, folder, all, resource, rule, variable, parameter, constant,
    action, decisionTable, scriptDecisionTable, crosstab, ul, flow, decisionTree,
    scorecard, complexscorecard, drl, lib, resourcePackage, packageConfig,
    ruleLib, decisionTableLib, decisionTreeLib, scorecardLib, flowLib, drlLib,
    // V6.20.0 P3:DMN/PMML 文件分类
    dmn, pmml
}
