package com.ruleforge.console.batchtest;

import java.util.Map;

/**
 * POST /ruleforge/batchtest/start 的请求体(V5.8.0)
 *
 * @param subjectType      FLOW / DATASOURCE(测什么)
 * @param subjectId        flowId 或 datasourceId
 * @param inputSourceType  FILE / DATASOURCE(input 从哪来)
 * @param inputSourceId    datasourceId(FLOW+DATASOURCE 或 DATASOURCE+DATASOURCE 时填)
 * @param inputConfig      各 InputSource 自己的配置(见 InputSourceConfig)
 * @param project          RuleForge 项目名(冗余存一份,方便按 project 过滤)
 * @param packageId        决策流所属包(FLOW 时必填)
 * @param flowId           决策流 id(FLOW 时必填)
 */
public record StartBatchTestRequest(
        String subjectType,
        Long subjectId,
        String inputSourceType,
        Long inputSourceId,
        Map<String, Object> inputConfig,
        String project,
        String packageId,
        String flowId
) {
}
