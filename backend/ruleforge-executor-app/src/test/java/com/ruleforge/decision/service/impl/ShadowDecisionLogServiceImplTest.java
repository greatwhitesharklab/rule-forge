package com.ruleforge.decision.service.impl;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.debug.MsgType;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.decision.entity.*;
import com.ruleforge.decision.repository.DecisionLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 陪跑决策日志批量写入
 *
 * ShadowDecisionLogServiceImpl 使用 DecisionLogRepository 批量写入规则日志和消息日志，
 * 与 DecisionLogServiceImpl 相同模式，用于陪跑（影子）决策。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShadowDecisionLogServiceImpl - 陪跑日志批量写入")
class ShadowDecisionLogServiceImplTest {

    @Mock private DecisionLogRepository decisionLogRepository;

    @InjectMocks
    private ShadowDecisionLogServiceImpl service;

    private static final String USER_ID = "testUser";
    private static final String ORDER_NO = "ORD001";
    private static final String FLOW_ID = "flow-001";
    private static final Long MAIN_FLOW_LOG_ID = 999L;

    private RuleInfo buildRuleInfo(String name, int salience) {
        RuleInfo info = mock(RuleInfo.class);
        lenient().when(info.getName()).thenReturn(name);
        lenient().when(info.getSalience()).thenReturn(salience);
        lenient().when(info.getActivationGroup()).thenReturn(null);
        lenient().when(info.getAgendaGroup()).thenReturn(null);
        lenient().when(info.getRuleflowGroup()).thenReturn(null);
        return info;
    }

    private ExecutionResponseImpl buildResponse(List<RuleInfo> matchedRules, List<RuleInfo> firedRules) {
        ExecutionResponseImpl resp = new ExecutionResponseImpl();
        resp.setDuration(100L);
        resp.addNodeName("node1");

        ExecutionResponseImpl ruleResp = new ExecutionResponseImpl();
        ruleResp.setDuration(50L);
        if (matchedRules != null) ruleResp.addMatchedRules(matchedRules);
        if (firedRules != null) ruleResp.setFiredRules(firedRules);
        resp.addRuleExecutionResponse(ruleResp);

        return resp;
    }

    private List<MessageItem> buildMessageItems(int count) {
        List<MessageItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            items.add(new MessageItem("shadow-msg-" + i, MsgType.VarAssign,
                    "leftVar" + i, "leftVal" + i, "rightVar" + i, "rightVal" + i));
        }
        return items;
    }

    @Nested
    @DisplayName("Scenario: 正常批量写入多条规则日志和消息日志")
    class BatchInsertMultipleItems {

        @Test
        @DisplayName("多条规则和消息日志通过 repository 批量写入")
        void shouldBatchInsertAllRuleAndMessageLogs() {
            // Given
            RuleInfo rule1 = buildRuleInfo("shadow-rule-1", 10);
            RuleInfo rule2 = buildRuleInfo("shadow-rule-2", 20);
            ExecutionResponseImpl response = buildResponse(List.of(rule1), List.of(rule2));
            List<MessageItem> messages = buildMessageItems(2);

            when(decisionLogRepository.insertShadowFlowLog(any(ShadowFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, ShadowFlowLog.class).setId(800L);
                return inv.getArgument(0, ShadowFlowLog.class);
            });

            // When
            service.saveShadowLog(
                    MAIN_FLOW_LOG_ID, USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of("input", 1), Map.of("output", 2), Map.of("entity", 3),
                    response, messages,
                    20L, 50L, 100L, 10, null, null);

            // Then
            ArgumentCaptor<List<ShadowRuleLog>> ruleCaptor = ArgumentCaptor.forClass(List.class);
            verify(decisionLogRepository).batchInsertShadowRuleLogs(ruleCaptor.capture());
            assertThat(ruleCaptor.getValue()).hasSize(2);

            ArgumentCaptor<List<ShadowMessageLog>> msgCaptor = ArgumentCaptor.forClass(List.class);
            verify(decisionLogRepository).batchInsertShadowMessageLogs(msgCaptor.capture());
            assertThat(msgCaptor.getValue()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Scenario: 没有规则日志也没有消息日志")
    class NoRuleOrMessageLogs {

        @Test
        @DisplayName("无规则和消息日志时不调用 batch 方法")
        void shouldSkipBatchWhenNoData() {
            // Given
            when(decisionLogRepository.insertShadowFlowLog(any(ShadowFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, ShadowFlowLog.class).setId(900L);
                return inv.getArgument(0, ShadowFlowLog.class);
            });

            // When — response 为 null
            service.saveShadowLog(
                    MAIN_FLOW_LOG_ID, USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of(), Map.of(), Map.of(),
                    null, null,
                    10L, 50L, 100L, 0, null, null);

            // Then
            verify(decisionLogRepository, times(1)).insertShadowFlowLog(any(ShadowFlowLog.class));
            verify(decisionLogRepository, times(1)).insertShadowFlowParams(any(ShadowFlowParams.class));
            verify(decisionLogRepository, never()).batchInsertShadowRuleLogs(anyList());
            verify(decisionLogRepository, never()).batchInsertShadowMessageLogs(anyList());
        }
    }

    @Nested
    @DisplayName("Scenario: 陪跑日志关联主决策日志 ID")
    class ShadowLogLinkedToMainLog {

        @Test
        @DisplayName("陪跑日志的 mainFlowLogId 正确关联")
        void shouldSetMainFlowLogIdOnShadowLog() {
            // Given
            when(decisionLogRepository.insertShadowFlowLog(any(ShadowFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, ShadowFlowLog.class).setId(850L);
                return inv.getArgument(0, ShadowFlowLog.class);
            });

            // When
            service.saveShadowLog(
                    MAIN_FLOW_LOG_ID, USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of(), Map.of(), Map.of(),
                    null, null,
                    10L, 50L, 100L, 0, null, null);

            // Then
            var captor = ArgumentCaptor.forClass(ShadowFlowLog.class);
            verify(decisionLogRepository).insertShadowFlowLog(captor.capture());
            ShadowFlowLog shadowLog = captor.getValue();
            assertThat(shadowLog.getMainFlowLogId()).isEqualTo(MAIN_FLOW_LOG_ID);
            assertThat(shadowLog.getUserId()).isEqualTo(USER_ID);
            assertThat(shadowLog.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(shadowLog.getFlowId()).isEqualTo(FLOW_ID);
        }
    }
}
