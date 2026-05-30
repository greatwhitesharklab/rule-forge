package com.ruleforge.console.app.service.impl;

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
import com.ruleforge.console.app.entity.ShadowFlowLog;
import com.ruleforge.console.app.entity.ShadowFlowParams;
import com.ruleforge.console.app.entity.ShadowMessageLog;
import com.ruleforge.console.app.entity.ShadowNodeLog;
import com.ruleforge.console.app.entity.ShadowRuleLog;
import com.ruleforge.console.app.mapper.ShadowFlowLogMapper;
import com.ruleforge.console.app.mapper.ShadowFlowParamsMapper;
import com.ruleforge.console.app.mapper.ShadowMessageLogMapper;
import com.ruleforge.console.app.mapper.ShadowNodeLogMapper;
import com.ruleforge.console.app.mapper.ShadowRuleLogMapper;
import com.ruleforge.console.app.service.IShadowDecisionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 陪跑决策流日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShadowDecisionLogServiceImpl implements IShadowDecisionLogService {

    private final ShadowFlowLogMapper flowLogMapper;
    private final ShadowFlowParamsMapper flowParamsMapper;
    private final ShadowNodeLogMapper nodeLogMapper;
    private final ShadowRuleLogMapper ruleLogMapper;
    private final ShadowMessageLogMapper messageLogMapper;
    private final SqlSessionFactory sqlSessionFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public void saveShadowLog(
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

        // 1. 保存陪跑主流水
        ShadowFlowLog flowLog = new ShadowFlowLog();
        flowLog.setMainFlowLogId(mainFlowLogId);
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

        flowLogMapper.insert(flowLog);
        Long flowLogId = flowLog.getId();

        // 1.1 保存参数数据
        ShadowFlowParams flowParams = new ShadowFlowParams();
        flowParams.setFlowLogId(flowLogId);
        flowParams.setUserId(userId);
        flowParams.setInputParams(toJson(inputParams));
        flowParams.setOutputParams(toJson(outputParams));
        flowParams.setEntityData(toJson(entityData));
        flowParams.setCreatedAt(now);
        flowParamsMapper.insert(flowParams);

        if (response == null) {
            return;
        }

        // 3. 收集所有规则日志 + 消息日志，用批量 INSERT
        List<ShadowRuleLog> allRuleLogs = new ArrayList<>();
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

        List<ShadowMessageLog> allMsgLogs = new ArrayList<>();
        if (execMessageItems != null && !execMessageItems.isEmpty()) {
            for (int i = 0; i < execMessageItems.size(); i++) {
                MessageItem item = execMessageItems.get(i);
                ShadowMessageLog msgLog = new ShadowMessageLog();
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

        // 批量写入
        batchInsert(allRuleLogs, allMsgLogs);

        log.info("陪跑日志保存成功: shadowFlowLogId={}, mainFlowLogId={}, userId={}, flowId={}, ruleLogs={}, msgLogs={}",
                flowLogId, mainFlowLogId, userId, flowId, allRuleLogs.size(), allMsgLogs.size());
    }

    private ShadowRuleLog buildRuleLog(Long flowLogId, String userId, int ruleNodeIndex,
                                       long durationMs, String ruleType, int ruleIndex,
                                       RuleInfo ruleInfo, Date now) {
        ShadowRuleLog ruleLog = new ShadowRuleLog();
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
        if (ruleInfo instanceof Rule) {
            Rule rule = (Rule) ruleInfo;
            Lhs lhs = rule.getLhs();
            if (lhs != null && lhs.getCriterion() != null) {
                ruleLog.setLhsCondition(buildCriterionDescription(lhs.getCriterion()));
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

    private void batchInsert(List<ShadowRuleLog> ruleLogs, List<ShadowMessageLog> msgLogs) {
        if ((ruleLogs == null || ruleLogs.isEmpty()) && (msgLogs == null || msgLogs.isEmpty())) {
            return;
        }
        try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH, false)) {
            try {
                if (ruleLogs != null && !ruleLogs.isEmpty()) {
                    ShadowRuleLogMapper batchRuleMapper = sqlSession.getMapper(ShadowRuleLogMapper.class);
                    for (ShadowRuleLog ruleLog : ruleLogs) {
                        batchRuleMapper.insert(ruleLog);
                    }
                }
                if (msgLogs != null && !msgLogs.isEmpty()) {
                    ShadowMessageLogMapper batchMsgMapper = sqlSession.getMapper(ShadowMessageLogMapper.class);
                    for (ShadowMessageLog msgLog : msgLogs) {
                        batchMsgMapper.insert(msgLog);
                    }
                }
                sqlSession.commit();
            } catch (Exception e) {
                sqlSession.rollback();
                throw e;
            }
        }
    }
}
