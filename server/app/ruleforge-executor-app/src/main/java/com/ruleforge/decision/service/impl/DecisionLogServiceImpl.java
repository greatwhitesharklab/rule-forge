package com.ruleforge.decision.service.impl;

import com.ruleforge.action.Action;
import com.ruleforge.action.VariableAssignAction;
import com.ruleforge.debug.MessageItem;
import com.ruleforge.model.rule.Rhs;
import com.ruleforge.model.rule.Rule;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.model.rule.Value;
import com.ruleforge.model.rule.lhs.BaseCriteria;
import com.ruleforge.model.rule.lhs.Criterion;
import com.ruleforge.model.rule.lhs.Junction;
import com.ruleforge.model.rule.lhs.Lhs;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.response.RuleExecutionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.decision.analytics.ClickHouseAnalyticsEvent;
import com.ruleforge.decision.dto.GrayResolution;
import com.ruleforge.decision.entity.DecisionFlowLog;
import com.ruleforge.decision.entity.DecisionFlowParams;
import com.ruleforge.decision.entity.DecisionMessageLog;
import com.ruleforge.decision.entity.DecisionNodeLog;
import com.ruleforge.decision.entity.DecisionRuleLog;
import com.ruleforge.decision.repository.DecisionLogRepository;
import com.ruleforge.decision.service.IDecisionLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 决策流执行日志服务
 */
@Slf4j
@Service
public class DecisionLogServiceImpl implements IDecisionLogService {

    private final DecisionLogRepository decisionLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Value("${clickhouse.analytics.enabled:true}")
    private boolean chEnabled;

    public DecisionLogServiceImpl(DecisionLogRepository decisionLogRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.decisionLogRepository = decisionLogRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 异步保存决策流执行日志
     */
    @Override
    @Async("decisionLogExecutor")
    @Transactional
    public void saveDecisionLogAsync(
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
    ) {
        try {
            saveDecisionLog(userId, orderNo, flowId, flowVersion, rulePackagePath, rulePackageVersion,
                    executionStatus, rejectReason, rejectCode, inputParams, outputParams, entityData,
                    response, execMessageItems, queryVariableDefTime, loadKnowledgeTime, createSessionTime, insertEntityTime,
                    flowExecutionTime, totalExecutionTime, totalLoadedFields, errorMessage, errorStackTrace,
                    grayResolution);
        } catch (Exception e) {
            log.error("异步保存决策流日志失败: userId={}, flowId={}", userId, flowId, e);
        }
    }

    /**
     * 同步保存决策流执行日志
     */
    @Override
    @Transactional
    public Long saveDecisionLog(
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
    ) {
        Date now = new Date();

        // 统计规则数
        int totalMatchedRules = 0;
        int totalFiredRules = 0;
        List<RuleExecutionResponse> ruleExecutionResponses = null;
        if (response != null) {
            ruleExecutionResponses = response.getRuleExecutionResponses();
            if (ruleExecutionResponses != null) {
                for (RuleExecutionResponse ruleResp : ruleExecutionResponses) {
                    if (ruleResp.getMatchedRules() != null) {
                        totalMatchedRules += ruleResp.getMatchedRules().size();
                    }
                    if (ruleResp.getFiredRules() != null) {
                        totalFiredRules += ruleResp.getFiredRules().size();
                    }
                }
            }
        }

        // 1. 保存主流水
        DecisionFlowLog flowLog = new DecisionFlowLog();
        flowLog.setUserId(userId);
        flowLog.setOrderNo(orderNo);
        flowLog.setFlowId(flowId);
        flowLog.setFlowVersion(flowVersion);
        flowLog.setRulePackagePath(rulePackagePath);
        flowLog.setRulePackageVersion(rulePackageVersion);
        flowLog.setExecutionStatus(executionStatus);
        flowLog.setRejectReason(rejectReason);
        flowLog.setRejectCode(rejectCode);
        flowLog.setNodeNames(response != null ? toJson(response.getNodeNames()) : null);
        flowLog.setExecutionTimeMs(response != null ? response.getDuration() : null);
        flowLog.setTotalTimeMs(totalExecutionTime);
        flowLog.setLoadKnowledgeTimeMs(loadKnowledgeTime);
        flowLog.setFlowExecutionTimeMs(flowExecutionTime);
        flowLog.setTotalMatchedRules(totalMatchedRules);
        flowLog.setTotalFiredRules(totalFiredRules);
        flowLog.setTotalLoadedFields(totalLoadedFields);
        flowLog.setErrorMessage(errorMessage);
        flowLog.setErrorStackTrace(errorStackTrace);
        flowLog.setCreatedAt(now);

        // 灰度标记
        if (grayResolution != null && grayResolution.isGrayHit()) {
            flowLog.setIsGray(true);
            flowLog.setGrayStrategyId(grayResolution.getStrategyId());
            flowLog.setGrayGitTag(grayResolution.getGitTag());
        } else {
            flowLog.setIsGray(false);
        }

        decisionLogRepository.insertFlowLog(flowLog);
        Long flowLogId = flowLog.getId();

        // 1.1 保存参数数据到单独的表
        DecisionFlowParams flowParams = new DecisionFlowParams();
        flowParams.setFlowLogId(flowLogId);
        flowParams.setUserId(userId);
        flowParams.setInputParams(toJson(inputParams));
        flowParams.setOutputParams(toJson(outputParams));
        flowParams.setEntityData(toJson(entityData));
        flowParams.setCreatedAt(now);
        decisionLogRepository.insertFlowParams(flowParams);

        if (response == null) {
            return flowLogId;
        }

        // 3. 收集所有规则日志 + 消息日志，用批量 INSERT
        List<DecisionRuleLog> allRuleLogs = new ArrayList<>();
        if (ruleExecutionResponses != null && !ruleExecutionResponses.isEmpty()) {
            for (int i = 0; i < ruleExecutionResponses.size(); i++) {
                RuleExecutionResponse ruleResp = ruleExecutionResponses.get(i);

                List<RuleInfo> matchedRules = ruleResp.getMatchedRules();
                if (matchedRules != null && !matchedRules.isEmpty()) {
                    for (int j = 0; j < matchedRules.size(); j++) {
                        allRuleLogs.add(buildRuleLog(flowLogId, userId, i, ruleResp.getDuration(),
                                "MATCHED", j, matchedRules.get(j), now));
                    }
                }

                List<RuleInfo> firedRules = ruleResp.getFiredRules();
                if (firedRules != null && !firedRules.isEmpty()) {
                    for (int j = 0; j < firedRules.size(); j++) {
                        allRuleLogs.add(buildRuleLog(flowLogId, userId, i, ruleResp.getDuration(),
                                "FIRED", j, firedRules.get(j), now));
                    }
                }
            }
        }

        List<DecisionMessageLog> allMsgLogs = new ArrayList<>();
        if (execMessageItems != null && !execMessageItems.isEmpty()) {
            for (int i = 0; i < execMessageItems.size(); i++) {
                MessageItem item = execMessageItems.get(i);
                DecisionMessageLog msgLog = new DecisionMessageLog();
                msgLog.setFlowLogId(flowLogId);
                msgLog.setUserId(userId);
                msgLog.setMsgIndex(i);
                msgLog.setMsgType(item.getType() != null ? item.getType().name() : null);
                msgLog.setMsg(item.getMsg());
                msgLog.setLeftVariable(item.getLeftVariable());
                msgLog.setLeftVariableValue(item.getLeftVariableValue());
                msgLog.setRightVariable(item.getRightVariable());
                msgLog.setRightVariableValue(item.getRightVariableValue());
                msgLog.setExecTime(item.getExecTime());
                msgLog.setCreatedAt(now);
                allMsgLogs.add(msgLog);
            }
        }

        // 批量写入：用 SqlSession batch 模式，一次 commit
        batchInsert(allRuleLogs, allMsgLogs);

        log.info("决策流日志保存成功: flowLogId={}, userId={}, flowId={}, ruleLogs={}, msgLogs={}",
                flowLogId, userId, flowId, allRuleLogs.size(), allMsgLogs.size());

        // Phase 8: 异步双写到 ClickHouse (失败不影响主路径)
        if (chEnabled) {
            try {
                eventPublisher.publishEvent(new ClickHouseAnalyticsEvent(flowLog, allRuleLogs));
            } catch (Exception e) {
                log.debug("ClickHouse event publish failed (non-fatal): {}", e.getMessage());
            }
        }

        return flowLogId;
    }

    private DecisionRuleLog buildRuleLog(Long flowLogId, String userId, int ruleNodeIndex,
                                         long durationMs, String ruleType, int ruleIndex,
                                         RuleInfo ruleInfo, Date now) {
        DecisionRuleLog ruleLog = new DecisionRuleLog();
        ruleLog.setFlowLogId(flowLogId);
        ruleLog.setUserId(userId);
        ruleLog.setRuleNodeIndex(ruleNodeIndex);
        ruleLog.setDurationMs(durationMs);
        ruleLog.setRuleType(ruleType);
        ruleLog.setRuleIndex(ruleIndex);
        ruleLog.setRuleName(ruleInfo.getName());
        ruleLog.setSalience(ruleInfo.getSalience());
        ruleLog.setActivationGroup(ruleInfo.getActivationGroup());
        ruleLog.setAgendaGroup(ruleInfo.getAgendaGroup());
        ruleLog.setRuleflowGroup(ruleInfo.getRuleflowGroup());

        // 获取 LHS/RHS
        log.debug("ruleInfo class: {}, instanceof Rule: {}", ruleInfo.getClass().getName(), ruleInfo instanceof Rule);
        if (ruleInfo instanceof Rule) {
            Rule rule = (Rule) ruleInfo;
            Lhs lhs = rule.getLhs();
            log.debug("rule.getLhs(): {}, lhs is null: {}", lhs, lhs == null);
            if (lhs != null) {
                Criterion criterion = lhs.getCriterion();
                log.debug("lhs.getCriterion(): {}, criterion is null: {}", criterion, criterion == null);
                if (criterion != null) {
                    ruleLog.setLhsCondition(buildCriterionDescription(criterion));
                }
            }
            Rhs rhs = rule.getRhs();
            if (rhs != null && rhs.getActions() != null) {
                List<String> actionDescs = new ArrayList<>();
                for (Action action : rhs.getActions()) {
                    actionDescs.add(buildActionDescription(action));
                }
                ruleLog.setRhsActions(toJson(actionDescs));
            }
        }

        ruleLog.setCreatedAt(now);
        return ruleLog;
    }

    private String buildCriterionDescription(Criterion criterion) {
        if (criterion == null) {
            return "null";
        }
        if (criterion instanceof BaseCriteria) {
            return ((BaseCriteria) criterion).getId();
        } else if (criterion instanceof Junction) {
            Junction junction = (Junction) criterion;
            List<Criterion> criteria = junction.getCriterions();
            if (criteria == null || criteria.isEmpty()) {
                return junction.getJunctionType() + "()";
            }
            List<String> parts = new ArrayList<>();
            for (Criterion c : criteria) {
                parts.add(buildCriterionDescription(c));
            }
            return junction.getJunctionType() + "(" + String.join(", ", parts) + ")";
        }
        return criterion.getClass().getSimpleName();
    }

    private String buildActionDescription(Action action) {
        if (action == null) {
            return "null";
        }
        if (action instanceof VariableAssignAction) {
            VariableAssignAction va = (VariableAssignAction) action;
            Value value = va.getValue();
            String valueDesc = value != null ? value.getId() : "null";
            return String.format("变量赋值: %s.%s = %s",
                    va.getVariableCategory(),
                    va.getVariableLabel() != null ? va.getVariableLabel() : va.getVariableName(),
                    valueDesc);
        }
        return String.format("type=%s", action.getActionType());
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON序列化失败: {}", e.getMessage());
            return obj.toString();
        }
    }

    /**
     * 批量写入规则日志和消息日志。
     */
    private void batchInsert(List<DecisionRuleLog> ruleLogs, List<DecisionMessageLog> msgLogs) {
        if ((ruleLogs == null || ruleLogs.isEmpty()) && (msgLogs == null || msgLogs.isEmpty())) {
            return;
        }
        if (ruleLogs != null && !ruleLogs.isEmpty()) {
            decisionLogRepository.batchInsertRuleLogs(ruleLogs);
        }
        if (msgLogs != null && !msgLogs.isEmpty()) {
            decisionLogRepository.batchInsertMessageLogs(msgLogs);
        }
    }
}
