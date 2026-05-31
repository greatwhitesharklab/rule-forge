package com.ruleforge.decision.dto;

import lombok.Data;

/**
 * 陪跑差异统计摘要 DTO
 */
@Data
public class ShadowDivergenceStats {

    private String rulePackagePath;

    /** 总对比次数 */
    private int totalComparisons;

    /** 有差异次数 */
    private int totalDivergent;

    /** 差异率 (0-100) */
    private double divergenceRate;

    /** 状态不一致次数 */
    private int statusDivergent;

    /** 结果不一致次数 */
    private int resultDivergent;

    /** 高严重度差异次数 */
    private int highSeverityCount;

    /** 中严重度差异次数 */
    private int mediumSeverityCount;

    /** 低严重度差异次数 */
    private int lowSeverityCount;
}
