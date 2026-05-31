package com.ruleforge.console.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author fred
 * @since 2021/12/15 10:39 AM
 */
@Data
public class DoTestDto {
    private List<List<Map<String, Object>>> data;
    private String flowId;
    private String files;
    private String project;
    private String packageId;
    private Long sessionId;
}
