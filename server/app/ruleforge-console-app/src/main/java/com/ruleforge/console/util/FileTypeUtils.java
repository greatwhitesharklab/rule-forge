package com.ruleforge.console.util;

import com.ruleforge.console.repository.model.FileType;
import com.ruleforge.console.repository.model.Type;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.ruleforge.console.repository.BaseRepositoryService.RES_PACKGE_FILE;
import static com.ruleforge.console.storage.RuleForgeBaseRepositoryService.PACKAGE_CONFIG_FILE;

/**
 * 文件类型工具类
 */
public class FileTypeUtils {

    private static final Map<String, FileType> EXTENSION_MAP = new LinkedHashMap<>();

    static {
        // Standard extensions (used by frontend)
        EXTENSION_MAP.put(".vl.xml", FileType.VariableLibrary);
        EXTENSION_MAP.put(".cl.xml", FileType.ConstantLibrary);
        EXTENSION_MAP.put(".pl.xml", FileType.ParameterLibrary);
        EXTENSION_MAP.put(".al.xml", FileType.ActionLibrary);
        EXTENSION_MAP.put(".rs.xml", FileType.Ruleset);
        EXTENSION_MAP.put(".rsl.xml", FileType.RulesetLib);
        EXTENSION_MAP.put(".ul.xml", FileType.UL);
        EXTENSION_MAP.put(".ul", FileType.UL);
        EXTENSION_MAP.put(".dt.xml", FileType.DecisionTable);
        EXTENSION_MAP.put(".ct.xml", FileType.Crosstab);
        EXTENSION_MAP.put(".dts.xml", FileType.ScriptDecisionTable);
        EXTENSION_MAP.put(".sct.xml", FileType.ScriptDecisionTable);
        EXTENSION_MAP.put(".dtree.xml", FileType.DecisionTree);
        EXTENSION_MAP.put(".sc", FileType.Scorecard);
        EXTENSION_MAP.put(".scc", FileType.ComplexScorecard);
        EXTENSION_MAP.put(".csc.xml", FileType.ComplexScorecard);
        EXTENSION_MAP.put(".rl.xml", FileType.RuleFlow);
        EXTENSION_MAP.put(".drl", FileType.Drl);
        // V6.20.0 P3:DMN 1.3 / PMML 4.4 标准决策模型(只读/导入)
        EXTENSION_MAP.put(".dmn", FileType.Dmn);
        EXTENSION_MAP.put(".pmml", FileType.Pmml);
    }

    /**
     * 根据文件名获取 FileType
     */
    public static FileType getFileTypeByFileName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String lower = name.toLowerCase();
        for (Map.Entry<String, FileType> entry : EXTENSION_MAP.entrySet()) {
            if (lower.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 根据文件名映射到对应的文件类型
     *
     * @param name 文件名
     * @return 文件类型
     */
    public static Type mapFileNameToType(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        FileType fileType = getFileTypeByFileName(name);
        if (fileType != null) {
            return mapFileTypeToType(fileType);
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(PACKAGE_CONFIG_FILE)) {
            return Type.packageConfig;
        }
        if (lower.endsWith(RES_PACKGE_FILE)) {
            return Type.resourcePackage;
        }
        return null;
    }

    private static Type mapFileTypeToType(FileType fileType) {
        switch (fileType) {
            case ActionLibrary:
                return Type.action;
            case VariableLibrary:
                return Type.variable;
            case ConstantLibrary:
                return Type.constant;
            case ParameterLibrary:
                return Type.parameter;
            case Ruleset:
            case RulesetLib:
                return Type.rule;
            case Drl:
                return Type.drl;
            case DecisionTable:
                return Type.decisionTable;
            case ScriptDecisionTable:
                return Type.scriptDecisionTable;
            case Crosstab:
                return Type.crosstab;
            case UL:
                return Type.ul;
            case RuleFlow:
                return Type.flow;
            case DecisionTree:
                return Type.decisionTree;
            case Scorecard:
                return Type.scorecard;
            case ComplexScorecard:
                return Type.complexscorecard;
            // V6.20.0 P3:DMN/PMML 文件 → Type.dmn/pmml(树 buildData 渲染)
            case Dmn:
                return Type.dmn;
            case Pmml:
                return Type.pmml;
            default:
                return null;
        }
    }
}
