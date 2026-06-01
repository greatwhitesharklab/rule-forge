package com.ruleforge.decision.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 灰度版本解析结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrayResolution {

    /** 最终使用的 git tag */
    private String gitTag;

    /** 是否命中灰度 */
    private boolean grayHit;

    /** 命中的策略 ID（null = 未命中） */
    private Long strategyId;

    public static GrayResolution baseline(String gitTag) {
        return new GrayResolution(gitTag, false, null);
    }

    public static GrayResolution gray(String targetGitTag, Long strategyId) {
        return new GrayResolution(targetGitTag, true, strategyId);
    }
}
