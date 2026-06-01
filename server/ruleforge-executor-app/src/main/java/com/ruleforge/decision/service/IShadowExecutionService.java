package com.ruleforge.decision.service;

import com.ruleforge.decision.entity.ShadowConfig;

/**
 * 陪跑执行服务接口
 */
public interface IShadowExecutionService {

    /**
     * 异步执行陪跑
     *
     * @param mainFlowLogId     主流水ID
     * @param userId            用户ID
     * @param orderNo           订单号
     * @param mainFlowId        主决策流ID
     * @param shadowConfig      陪跑配置
     */
    void executeShadowAsync(
            Long mainFlowLogId,
            String userId,
            String orderNo,
            String mainFlowId,
            ShadowConfig shadowConfig
    );
}
