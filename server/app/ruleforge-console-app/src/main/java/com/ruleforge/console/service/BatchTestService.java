package com.ruleforge.console.service;

import com.ruleforge.runtime.KnowledgePackage;

import java.util.List;
import java.util.Map;

/**
 * 批量测试服务接口(V5.8.0 保持兼容)
 *
 * 实现见 BatchTestServiceImpl。沿用 executeBatchAsync + getSessionProgress 两个老方法
 * 以保证 V5.8.0 之前的测试 / 老 UI 还能工作。
 *
 * V5.8.0 起 controller 不再直接调这两个方法,而是走新的 startBatchTest 流程
 * (创 session + 选 subject + 选 input source + 异步执行)。
 */
public interface BatchTestService {

    // ── 老方法(V5.8.0 之前)— 兼容保留 ─────────────────────────────────

    /**
     * 异步执行批量测试(老路径,session 已存在,rows 已写入)
     */
    void executeBatchAsync(Long sessionId, KnowledgePackage knowledgePackage,
                           String flowId, List<com.ruleforge.model.library.variable.VariableCategory> variableCategories);

    /**
     * 查询会话进度
     */
    Map<String, Object> getSessionProgress(Long sessionId);
}
