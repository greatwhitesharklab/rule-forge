package com.ruleforge.console.batchtest;

import java.util.Map;

/**
 * "被测对象"抽象(V5.8.0 多态化核心)
 *
 * 一个 BatchTestSubject 就是一个"被测目标"的具体执行器。Spring 容器里
 * 所有实现按 type 注册到 BatchTestService 的 map,根据 session.subjectType 选取。
 *
 * 当前实现:
 *   - {@link FlowBatchTestSubject}         跑 KnowledgeSession(决策流)
 *
 * 未来扩展:
 *   - DatasourceOnlyBatchTestSubject       直接调 DatasourceRoutingProvider,
 *                                            不经过决策流(数据源 SLA / 字段映射验证)
 *   - ScorecardBatchTestSubject            跑评分卡
 *   - ApiE2EBatchTestSubject              端到端 API 链路
 *
 * 与 {@link InputSource} 正交:subject 决定"测什么",inputSource 决定"input 从哪来"。
 * 同一组 subject + inputSource 组合成一个完整的 batch test session。
 */
public interface BatchTestSubject {

    /**
     * Subject type 标识,跟 BatchTestSessionEntity.subjectType 一一对应。
     * Spring 容器内唯一,用于 BatchTestService 路由。
     */
    String getType();

    /**
     * 执行单条测试。
     *
     * 实现方负责:
     *   - 拿 ctx.input 解析成自己需要的输入类型(Flow: ApplicationAllVariableCategoryMap,Datasource: Map<String,Object>)
     *   - 调底层引擎(KnowledgeSession / DatasourceRoutingProvider / ...)
     *   - 包成 SubjectResult 返回(包含输出 + 延迟 + 错误码)
     *
     * 框架负责:
     *   - 异步调度(由 BatchTestService 触发)
     *   - 异常兜底(实现方不抛异常,把 errorCode/errorMessage 填到 result 里)
     *   - 结果落库(由 BatchTestService 把 result 写到 nd_batch_test_row)
     *
     * @param ctx 包含 sessionId / rowId / input / params(实现方需要的额外参数,如 flowMap / packageId)
     */
    SubjectResult execute(SubjectExecutionContext ctx);
}
