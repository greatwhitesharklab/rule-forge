package com.ruleforge.console.repository.model;

public enum LibType {
    res, ruleset, decisiontable, decisiontree, scorecard, ruleflow, drl, all,
    // V6.20.0 P3:DMN/PMML(预留 lib 容器,P3 暂不创建 UI 分类,只让文件被发现)
    dmn, pmml,
    // V7.0.0:V1 决策流(.json,React Flow 画布)
    v1flow
}
