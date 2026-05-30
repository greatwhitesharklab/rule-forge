package com.ruleforge.console.app.service.impl;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.debug.MsgType;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.console.app.entity.*;
import com.ruleforge.console.app.mapper.*;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 陪跑决策日志批量写入
 *
 * ShadowDecisionLogServiceImpl 使用 SqlSession BATCH 模式批量写入规则日志和消息日志，
 * 与 DecisionLogServiceImpl 相同模式，用于陪跑（影子）决策。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ShadowDecisionLogServiceImpl - 陪跑日志批量写入")
class ShadowDecisionLogServiceImplTest {

    @Mock private ShadowFlowLogMapper flowLogMapper;
    @Mock private ShadowFlowParamsMapper flowParamsMapper;
    @Mock private ShadowNodeLogMapper nodeLogMapper;
    @Mock private ShadowRuleLogMapper ruleLogMapper;
    @Mock private ShadowMessageLogMapper messageLogMapper;
    @Mock private SqlSessionFactory sqlSessionFactory;
    @Mock private SqlSession sqlSession;
    @Mock private ShadowRuleLogMapper batchRuleMapper;
    @Mock private ShadowMessageLogMapper batchMsgMapper;

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

        // Given 多条规则日志和消息日志
        // When saveShadowLog 被调用
        // Then 所有日志通过 SqlSession BATCH 模式一次性写入
        @Test
        @DisplayName("多条规则和消息日志通过 BATCH 模式一次 commit 写入")
        void shouldBatchInsertAllRuleAndMessageLogs() {
            // Given
            RuleInfo rule1 = buildRuleInfo("shadow-rule-1", 10);
            RuleInfo rule2 = buildRuleInfo("shadow-rule-2", 20);
            ExecutionResponseImpl response = buildResponse(List.of(rule1), List.of(rule2));
            List<MessageItem> messages = buildMessageItems(2);

            when(flowLogMapper.insert(any(ShadowFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, ShadowFlowLog.class).setId(800L);
                return 1;
            });
            when(sqlSessionFactory.openSession(ExecutorType.BATCH, false)).thenReturn(sqlSession);
            when(sqlSession.getMapper(ShadowRuleLogMapper.class)).thenReturn(batchRuleMapper);
            when(sqlSession.getMapper(ShadowMessageLogMapper.class)).thenReturn(batchMsgMapper);

            // When
            service.saveShadowLog(
                    MAIN_FLOW_LOG_ID, USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of("input", 1), Map.of("output", 2), Map.of("entity", 3),
                    response, messages,
                    20L, 50L, 100L, 10, null, null);

            // Then
            verify(sqlSessionFactory).openSession(ExecutorType.BATCH, false);
            verify(batchRuleMapper, times(2)).insert(any(ShadowRuleLog.class));
            verify(batchMsgMapper, times(2)).insert(any(ShadowMessageLog.class));
            verify(sqlSession, times(1)).commit();
            verify(sqlSession, never()).rollback();
        }
    }

    @Nested
    @DisplayName("Scenario: 没有规则日志也没有消息日志")
    class NoRuleOrMessageLogs {

        // Given response 为 null
        // When saveShadowLog 被调用
        // Then batchInsert 不执行任何数据库操作
        @Test
        @DisplayName("无规则和消息日志时不打开 SqlSession")
        void shouldSkipBatchWhenNoData() {
            // Given
            when(flowLogMapper.insert(any(ShadowFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, ShadowFlowLog.class).setId(900L);
                return 1;
            });

            // When — response 为 null
            service.saveShadowLog(
                    MAIN_FLOW_LOG_ID, USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of(), Map.of(), Map.of(),
                    null, null,
                    10L, 50L, 100L, 0, null, null);

            // Then
            verify(flowLogMapper, times(1)).insert(any(ShadowFlowLog.class));
            verify(flowParamsMapper, times(1)).insert(any(ShadowFlowParams.class));
            verify(sqlSessionFactory, never()).openSession(any(ExecutorType.class), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Scenario: 批量写入过程中抛出异常")
    class BatchInsertError {

        // Given 批量写入过程中抛出异常
        // When saveShadowLog 被调用
        // Then sqlSession.rollback() 被调用，异常向上传播
        @Test
        @DisplayName("批量写入异常时执行 rollback 并传播异常")
        void shouldRollbackAndRethrowOnBatchError() {
            // Given
            RuleInfo rule = buildRuleInfo("shadow-rule-err", 10);
            ExecutionResponseImpl response = buildResponse(List.of(rule), null);

            when(flowLogMapper.insert(any(ShadowFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, ShadowFlowLog.class).setId(950L);
                return 1;
            });
            when(sqlSessionFactory.openSession(ExecutorType.BATCH, false)).thenReturn(sqlSession);
            when(sqlSession.getMapper(ShadowRuleLogMapper.class)).thenReturn(batchRuleMapper);
            doThrow(new RuntimeException("Shadow DB error")).when(batchRuleMapper).insert(any(ShadowRuleLog.class));

            // When / Then
            assertThatThrownBy(() -> service.saveShadowLog(
                    MAIN_FLOW_LOG_ID, USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of(), Map.of(), Map.of(),
                    response, null,
                    10L, 50L, 100L, 0, null, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Shadow DB error");

            verify(sqlSession, times(1)).rollback();
            verify(sqlSession, never()).commit();
        }
    }

    @Nested
    @DisplayName("Scenario: 陪跑日志关联主决策日志 ID")
    class ShadowLogLinkedToMainLog {

        // Given mainFlowLogId 有值
        // When saveShadowLog 被调用
        // Then ShadowFlowLog 的 mainFlowLogId 正确设置
        @Test
        @DisplayName("陪跑日志的 mainFlowLogId 正确关联")
        void shouldSetMainFlowLogIdOnShadowLog() {
            // Given
            when(flowLogMapper.insert(any(ShadowFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, ShadowFlowLog.class).setId(850L);
                return 1;
            });

            // When
            service.saveShadowLog(
                    MAIN_FLOW_LOG_ID, USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of(), Map.of(), Map.of(),
                    null, null,
                    10L, 50L, 100L, 0, null, null);

            // Then
            var captor = org.mockito.ArgumentCaptor.forClass(ShadowFlowLog.class);
            verify(flowLogMapper).insert(captor.capture());
            ShadowFlowLog shadowLog = captor.getValue();
            assertThat(shadowLog.getMainFlowLogId()).isEqualTo(MAIN_FLOW_LOG_ID);
            assertThat(shadowLog.getUserId()).isEqualTo(USER_ID);
            assertThat(shadowLog.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(shadowLog.getFlowId()).isEqualTo(FLOW_ID);
        }
    }
}
