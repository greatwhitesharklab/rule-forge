package com.ruleforge.decision.service;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.runtime.response.ExecutionResponseImpl;

import java.util.List;
import java.util.Map;

/**
 * 陪跑决策流日志服务接口
 */
public interface IShadowDecisionLogService {

    /**
     * 保存陪跑决策流日志，返回 shadowFlowLogId
     */
    Long saveShadowLog(
            Long mainFlowLogId,
            String userId,
            String orderNo,
            String flowId,
            String flowVersion,
            String rulePackagePath,
            String rulePackageVersion,
            String executionStatus,
            String rejectReason,
            String rejectCode,
            Map<String, Object> inputParams,
            Map<String, Object> outputParams,
            Map<String, Object> entityData,
            ExecutionResponseImpl response,
            List<MessageItem> execMessageItems,
            long loadKnowledgeTime,
            long flowExecutionTime,
            long totalExecutionTime,
            int totalLoadedFields,
            String errorMessage,
            String errorStackTrace
    );
}
