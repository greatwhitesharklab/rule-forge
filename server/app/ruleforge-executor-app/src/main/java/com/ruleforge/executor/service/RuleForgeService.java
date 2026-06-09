package com.ruleforge.executor.service;

import java.util.List;
import java.util.Map;

public interface RuleForgeService {
    Map<String, Object> doTest(String project, String packageId, String flowId, List<Map<String, Object>> mapList);
}
