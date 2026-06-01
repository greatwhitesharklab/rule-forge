package com.ruleforge.decision.service;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.decision.dto.GrayResolution;
import com.ruleforge.runtime.response.ExecutionResponseImpl;

import java.util.List;
import java.util.Map;

/**
 * 决策流执行日志服务接口
 */
public interface IDecisionLogService {

    /**
     * 异步保存决策流执行日志
     */
    void saveDecisionLogAsync(
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
            long queryVariableDefTime,
            long loadKnowledgeTime,
            long createSessionTime,
            long insertEntityTime,
            long flowExecutionTime,
            long totalExecutionTime,
            int totalLoadedFields,
            String errorMessage,
            String errorStackTrace,
            GrayResolution grayResolution
    );

    /**
     * 同步保存决策流执行日志
     * @return flowLogId 主流水ID
     */
    Long saveDecisionLog(
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
            long queryVariableDefTime,
            long loadKnowledgeTime,
            long createSessionTime,
            long insertEntityTime,
            long flowExecutionTime,
            long totalExecutionTime,
            int totalLoadedFields,
            String errorMessage,
            String errorStackTrace,
            GrayResolution grayResolution
    );
}
