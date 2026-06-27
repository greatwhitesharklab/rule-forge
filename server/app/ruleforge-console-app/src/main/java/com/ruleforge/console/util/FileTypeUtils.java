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
        // V7.0.0→V7.5.1:V1 决策流后缀统一 .v1flow.json(跟其他 V1 文件 .v1xx.json 一致)。
        // 保留 .json 兼容旧文件(loan_approval.json 等)。
        EXTENSION_MAP.put(".v1flow.json", FileType.V1Flow);
        EXTENSION_MAP.put(".json", FileType.V1Flow);
        // V7.4:V1 库(.v1lib.json,双后缀避免跟 V1Flow .json 冲突;getFileTypeByFileName 长后缀优先)
        EXTENSION_MAP.put(".v1lib.json", FileType.V1Library);
        // V7.5:V1 规则独立文件(.v1rs.json/.v1dt.json/.v1sc.json,决策流引用)
        EXTENSION_MAP.put(".v1rs.json", FileType.V1RuleSet);
        EXTENSION_MAP.put(".v1dt.json", FileType.V1DecisionTable);
        EXTENSION_MAP.put(".v1sc.json", FileType.V1ScoreCard);
    }

    /**
     * 根据文件名获取 FileType(长后缀优先:.v1lib.json 先于 .json 匹配,避免 HashMap 遍历序不定)。
     */
    public static FileType getFileTypeByFileName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        String lower = name.toLowerCase();
        FileType best = null;
        int bestLen = 0;
        for (Map.Entry<String, FileType> entry : EXTENSION_MAP.entrySet()) {
            String key = entry.getKey();
            if (lower.endsWith(key) && key.length() > bestLen) {
                best = entry.getValue();
                bestLen = key.length();
            }
        }
        return best;
    }

    /**
     * 是否 V1 JSON 资产(`.v1flow.json`/`.v1lib.json`/`.v1rs.json`/`.v1dt.json`/`.v1sc.json`,含 `.json` 兼容旧)。
     *
     * <p>V1 文件是 JSON(非老 XML 规则),{@code /common/saveFile} 的 {@code parseXml} + deserializer
     * XML 校验对 JSON 必抛错 → 静默不存(V7.7 E2E 发现的 bug)。V1 文件经此判定后跳过 XML 校验直接存。
     */
    public static boolean isV1JsonAsset(String name) {
        FileType ft = getFileTypeByFileName(name);
        return ft == FileType.V1Flow || ft == FileType.V1Library
                || ft == FileType.V1RuleSet || ft == FileType.V1DecisionTable
                || ft == FileType.V1ScoreCard;
    }

    /**
     * 是否 JSON 内容(以 {@code {} 或 [] 开头,空白后第一个非空字符是 {@code {} 或 {@code [})。
     *
     * <p>V7.7.1 {@link #isV1JsonAsset(String)} 只看路径后缀;但 V1 文件存在 createFile 落 DB
     * 时**不带后缀**(`/proj/demo` 而非 `/proj/demo.v1flow.json`)— 后端 createFile
     * 不会补 {@code .json}。前端 saveFile 传的 path 也常无后缀。靠路径识别不全,补一道
     * content sniff:V1 资产都是 JSON,JSON 头必是 {@code {} 或 {@code [}。
     * {@code /common/saveFile} 遇到 JSON 内容直接跳过 XML 校验。
     */
    public static boolean isJsonContent(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == '{' || c == '[';
        }
        return false;
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
            // V7.0.0:V1 决策流文件 → Type.v1flow(树 buildData 渲染 + handleFileOpen 开画布)
            case V1Flow:
                return Type.v1flow;
            // V7.4:V1 库 → Type.v1library(树 buildData v1library + handleFileOpen 开库编辑器)
            case V1Library:
                return Type.v1library;
            // V7.5:V1 规则独立文件 → Type.v1ruleset/v1decisiontable/v1scorecard(树 buildData 渲染 + handleFileOpen 开编辑器)
            case V1RuleSet:
                return Type.v1ruleset;
            case V1DecisionTable:
                return Type.v1decisiontable;
            case V1ScoreCard:
                return Type.v1scorecard;
            default:
                return null;
        }
    }
}
