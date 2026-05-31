package com.ruleforge.console.service;

import com.ruleforge.model.library.variable.VariableCategory;
import com.ruleforge.runtime.KnowledgePackage;

import java.util.List;
import java.util.Map;

/**
 * 批量测试异步执行服务
 */
public interface BatchTestService {

    /**
     * 异步执行批量测试
     */
    void executeBatchAsync(Long sessionId, KnowledgePackage knowledgePackage,
                           String flowId, List<VariableCategory> variableCategories);

    /**
     * 查询会话进度
     */
    Map<String, Object> getSessionProgress(Long sessionId);
}
