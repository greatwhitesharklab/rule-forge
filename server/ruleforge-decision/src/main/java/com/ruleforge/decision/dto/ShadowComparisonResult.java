package com.ruleforge.decision.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 陪跑对比结果 DTO（REST 返回用）
 */
@Data
public class ShadowComparisonResult {

    private Long id;
    private Long mainFlowLogId;
    private Long shadowFlowLogId;
    private Long shadowConfigId;

    /** 执行状态是否一致 */
    private Boolean statusMatch;
    private String mainExecutionStatus;
    private String shadowExecutionStatus;

    /** 决策结果是否一致 */
    private Boolean resultMatch;
    private String mainRejectCode;
    private String shadowRejectCode;

    /** 输出字段差异明细 */
    private List<FieldDivergence> outputDivergence;

    /** 规则执行差异明细 */
    private RuleDivergenceDetail ruleDivergence;

    /** 是否有差异 */
    private Boolean hasDivergence;

    /** 差异严重度: NONE/LOW/MEDIUM/HIGH */
    private String divergenceSeverity;

    /** 耗时对比 */
    private Long mainTotalTimeMs;
    private Long shadowTotalTimeMs;

    private String userId;
    private String orderNo;
    private String rulePackagePath;
    private String createdAt;

    /**
     * 输出字段差异项
     */
    @Data
    public static class FieldDivergence {
        private String field;
        private String mainValue;
        private String shadowValue;
    }

    /**
     * 规则执行差异详情
     */
    @Data
    public static class RuleDivergenceDetail {
        private List<String> onlyInMain;
        private List<String> onlyInShadow;
        private Map<String, Integer> countDiff;
    }
}
