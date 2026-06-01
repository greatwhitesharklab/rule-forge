package com.ruleforge.console.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author Fred
 * @since 2025/8/26 14:07
 */
@Data
public class FastTestDto {

    private String appId;
    private String projectId;
    private String filePath;
    private String ruleName;
    private String flowId;
    private List<Map<String, Object>> data;
}
