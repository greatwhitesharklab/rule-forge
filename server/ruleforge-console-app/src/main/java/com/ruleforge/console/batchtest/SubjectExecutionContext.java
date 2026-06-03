package com.ruleforge.console.batchtest;

import java.util.Map;

/**
 * BatchTestSubject.execute() 的入参包(V5.8.0)
 *
 * @param sessionId  当前 nd_batch_test_session.id(供实现方记日志)
 * @param rowId      当前 nd_batch_test_row.id(供异步回调定位到行)
 * @param input      解析后的输入(Flow 模式是 ApplicationAllVariableCategoryMap,Datasource 模式是 Map<String,Object>)
 * @param params     实现方需要的额外上下文:
 *                      - FLOW:    { packageId, knowledgePackage, flowId, flowMap }
 *                      - DATASOURCE: { datasourceId, clazz }
 *                   key 不强制,实现方自己约定
 */
public record SubjectExecutionContext(
        Long sessionId,
        Long rowId,
        Object input,
        Map<String, Object> params
) {
}
