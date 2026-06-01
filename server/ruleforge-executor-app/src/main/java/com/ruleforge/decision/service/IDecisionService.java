package com.ruleforge.decision.service;

import com.ruleforge.decision.dto.DecisionRequest;
import com.ruleforge.decision.dto.DecisionResponse;

/**
 * 贷款决策评估服务接口
 */
public interface IDecisionService {

    /**
     * 执行贷款决策评估
     *
     * @param request 评估请求
     * @return 评估响应
     */
    DecisionResponse evaluate(DecisionRequest request);
}
