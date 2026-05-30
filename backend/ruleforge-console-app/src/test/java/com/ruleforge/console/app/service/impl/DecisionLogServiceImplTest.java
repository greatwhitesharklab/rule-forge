package com.ruleforge.console.app.service.impl;

import com.ruleforge.debug.MessageItem;
import com.ruleforge.debug.MsgType;
import com.ruleforge.model.rule.RuleInfo;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.response.RuleExecutionResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 决策日志批量写入
 *
 * DecisionLogServiceImpl 使用 SqlSession BATCH 模式批量写入规则日志和消息日志，
 * 替代逐条 mapper.insert()，减少 DB 往返次数。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DecisionLogServiceImpl - 决策日志批量写入")
class DecisionLogServiceImplTest {

    @Mock private DecisionFlowLogMapper flowLogMapper;
    @Mock private DecisionFlowParamsMapper flowParamsMapper;
    @Mock private DecisionNodeLogMapper nodeLogMapper;
    @Mock private DecisionRuleLogMapper ruleLogMapper;
    @Mock private DecisionMessageLogMapper messageLogMapper;
    @Mock private SqlSessionFactory sqlSessionFactory;
    @Mock private SqlSession sqlSession;
    @Mock private DecisionRuleLogMapper batchRuleMapper;
    @Mock private DecisionMessageLogMapper batchMsgMapper;

    @InjectMocks
    private DecisionLogServiceImpl service;

    private static final String USER_ID = "testUser";
    private static final String ORDER_NO = "ORD001";
    private static final String FLOW_ID = "flow-001";

    private RuleInfo buildRuleInfo(String name, int salience, String activationGroup, String agendaGroup, String ruleflowGroup) {
        RuleInfo info = mock(RuleInfo.class);
        lenient().when(info.getName()).thenReturn(name);
        lenient().when(info.getSalience()).thenReturn(salience);
        lenient().when(info.getActivationGroup()).thenReturn(activationGroup);
        lenient().when(info.getAgendaGroup()).thenReturn(agendaGroup);
        lenient().when(info.getRuleflowGroup()).thenReturn(ruleflowGroup);
        return info;
    }

    private ExecutionResponseImpl buildResponse(List<RuleInfo> matchedRules, List<RuleInfo> firedRules) {
        ExecutionResponseImpl resp = new ExecutionResponseImpl();
        resp.setDuration(100L);
        resp.addNodeName("node1");

        // 包装为 RuleExecutionResponse
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
            items.add(new MessageItem("msg-" + i, MsgType.Condition,
                    "leftVar" + i, "leftVal" + i, "rightVar" + i, "rightVal" + i));
        }
        return items;
    }

    @Nested
    @DisplayName("Scenario: 正常批量写入多条规则日志和消息日志")
    class BatchInsertMultipleItems {

        // Given 多条规则日志（matchedRules + firedRules）和消息日志
        // When saveDecisionLog 被调用
        // Then 所有规则日志和消息日志通过 SqlSession BATCH 模式一次性写入
        @Test
        @DisplayName("多条规则和消息日志通过 BATCH 模式一次 commit 写入")
        void shouldBatchInsertAllRuleAndMessageLogs() {
            // Given
            RuleInfo matchedRule = buildRuleInfo("rule-matched-1", 10, "actGrp", "agendaGrp", "rfGrp");
            RuleInfo firedRule = buildRuleInfo("rule-fired-1", 20, null, null, null);
            ExecutionResponseImpl response = buildResponse(
                    List.of(matchedRule), List.of(firedRule));
            List<MessageItem> messages = buildMessageItems(3);

            when(flowLogMapper.insert(any(DecisionFlowLog.class))).thenAnswer(inv -> {
                DecisionFlowLog log = inv.getArgument(0);
                log.setId(100L);
                return 1;
            });
            when(sqlSessionFactory.openSession(ExecutorType.BATCH, false)).thenReturn(sqlSession);
            when(sqlSession.getMapper(DecisionRuleLogMapper.class)).thenReturn(batchRuleMapper);
            when(sqlSession.getMapper(DecisionMessageLogMapper.class)).thenReturn(batchMsgMapper);

            // When
            Long result = service.saveDecisionLog(
                    USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of("input", 1), Map.of("output", 2), Map.of("entity", 3),
                    response, messages,
                    10L, 20L, 5L, 3L, 50L, 100L, 10, null, null);

            // Then
            assertThat(result).isEqualTo(100L);

            // 验证 BATCH SqlSession 被打开
            verify(sqlSessionFactory).openSession(ExecutorType.BATCH, false);

            // 验证规则日志: 1 matched + 1 fired = 2 次 insert
            verify(batchRuleMapper, times(2)).insert(any(DecisionRuleLog.class));

            // 验证消息日志: 3 次 insert
            verify(batchMsgMapper, times(3)).insert(any(DecisionMessageLog.class));

            // 验证只 commit 一次
            verify(sqlSession, times(1)).commit();
            verify(sqlSession, never()).rollback();
        }
    }

    @Nested
    @DisplayName("Scenario: 没有规则日志也没有消息日志")
    class NoRuleOrMessageLogs {

        // Given response 为 null（无规则执行结果）
        // When saveDecisionLog 被调用
        // Then batchInsert 不执行任何数据库操作（SqlSession 不打开）
        @Test
        @DisplayName("无规则和消息日志时不打开 SqlSession")
        void shouldSkipBatchWhenNoData() {
            // Given
            when(flowLogMapper.insert(any(DecisionFlowLog.class))).thenAnswer(inv -> {
                DecisionFlowLog log = inv.getArgument(0);
                log.setId(200L);
                return 1;
            });

            // When — response 为 null
            Long result = service.saveDecisionLog(
                    USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of("input", 1), Map.of("output", 2), Map.of("entity", 3),
                    null, null,
                    10L, 20L, 5L, 3L, 50L, 100L, 10, null, null);

            // Then
            assertThat(result).isEqualTo(200L);

            // 主流水和参数各插入一次
            verify(flowLogMapper, times(1)).insert(any(DecisionFlowLog.class));
            verify(flowParamsMapper, times(1)).insert(any(DecisionFlowParams.class));

            // 不打开 BATCH SqlSession
            verify(sqlSessionFactory, never()).openSession(any(ExecutorType.class), anyBoolean());
        }
    }

    @Nested
    @DisplayName("Scenario: 只有规则日志，没有消息日志")
    class OnlyRuleLogs {

        // Given 有规则执行结果但 execMessageItems 为空
        // When saveDecisionLog 被调用
        // Then 只有规则日志通过 BATCH 写入，消息日志 mapper 不被调用
        @Test
        @DisplayName("仅规则日志时只写入规则 mapper")
        void shouldOnlyInsertRuleLogsWhenNoMessages() {
            // Given
            RuleInfo matchedRule = buildRuleInfo("rule-1", 10, null, null, null);
            ExecutionResponseImpl response = buildResponse(List.of(matchedRule), null);

            when(flowLogMapper.insert(any(DecisionFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, DecisionFlowLog.class).setId(300L);
                return 1;
            });
            when(sqlSessionFactory.openSession(ExecutorType.BATCH, false)).thenReturn(sqlSession);
            when(sqlSession.getMapper(DecisionRuleLogMapper.class)).thenReturn(batchRuleMapper);

            // When — messages 为 null
            Long result = service.saveDecisionLog(
                    USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of(), Map.of(), Map.of(),
                    response, null,
                    10L, 20L, 5L, 3L, 50L, 100L, 10, null, null);

            // Then
            assertThat(result).isEqualTo(300L);

            // 规则日志写入
            verify(batchRuleMapper, times(1)).insert(any(DecisionRuleLog.class));

            // 消息日志 mapper 未获取
            verify(sqlSession, never()).getMapper(DecisionMessageLogMapper.class);

            // commit 一次
            verify(sqlSession, times(1)).commit();
        }
    }

    @Nested
    @DisplayName("Scenario: 只有消息日志，没有规则日志")
    class OnlyMessageLogs {

        // Given response 中无 matchedRules 和 firedRules，但有 execMessageItems
        // When saveDecisionLog 被调用
        // Then 只有消息日志通过 BATCH 写入，规则日志 mapper 不被调用
        @Test
        @DisplayName("仅消息日志时只写入消息 mapper")
        void shouldOnlyInsertMessageLogsWhenNoRules() {
            // Given — response 中没有规则
            ExecutionResponseImpl response = new ExecutionResponseImpl();
            response.setDuration(100L);
            ExecutionResponseImpl emptyRuleResp = new ExecutionResponseImpl();
            response.addRuleExecutionResponse(emptyRuleResp);

            List<MessageItem> messages = buildMessageItems(2);

            when(flowLogMapper.insert(any(DecisionFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, DecisionFlowLog.class).setId(400L);
                return 1;
            });
            when(sqlSessionFactory.openSession(ExecutorType.BATCH, false)).thenReturn(sqlSession);
            when(sqlSession.getMapper(DecisionMessageLogMapper.class)).thenReturn(batchMsgMapper);

            // When
            Long result = service.saveDecisionLog(
                    USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of(), Map.of(), Map.of(),
                    response, messages,
                    10L, 20L, 5L, 3L, 50L, 100L, 10, null, null);

            // Then
            assertThat(result).isEqualTo(400L);

            // 规则日志 mapper 未获取
            verify(sqlSession, never()).getMapper(DecisionRuleLogMapper.class);

            // 消息日志写入 2 条
            verify(batchMsgMapper, times(2)).insert(any(DecisionMessageLog.class));

            verify(sqlSession, times(1)).commit();
        }
    }

    @Nested
    @DisplayName("Scenario: 批量写入过程中抛出异常")
    class BatchInsertError {

        // Given 规则日志写入过程中 mapper.insert 抛出异常
        // When saveDecisionLog 被调用
        // Then sqlSession.rollback() 被调用，异常向上传播
        @Test
        @DisplayName("批量写入异常时执行 rollback 并传播异常")
        void shouldRollbackAndRethrowOnBatchError() {
            // Given
            RuleInfo matchedRule = buildRuleInfo("rule-1", 10, null, null, null);
            ExecutionResponseImpl response = buildResponse(List.of(matchedRule), null);

            when(flowLogMapper.insert(any(DecisionFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, DecisionFlowLog.class).setId(500L);
                return 1;
            });
            when(sqlSessionFactory.openSession(ExecutorType.BATCH, false)).thenReturn(sqlSession);
            when(sqlSession.getMapper(DecisionRuleLogMapper.class)).thenReturn(batchRuleMapper);
            doThrow(new RuntimeException("DB connection lost")).when(batchRuleMapper).insert(any(DecisionRuleLog.class));

            // When / Then
            assertThatThrownBy(() -> service.saveDecisionLog(
                    USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of(), Map.of(), Map.of(),
                    response, null,
                    10L, 20L, 5L, 3L, 50L, 100L, 10, null, null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DB connection lost");

            // rollback 被调用
            verify(sqlSession, times(1)).rollback();
            // commit 未被调用
            verify(sqlSession, never()).commit();
        }
    }

    @Nested
    @DisplayName("Scenario: 规则日志字段正确映射")
    class RuleLogFieldMapping {

        // Given RuleInfo 包含 name、salience、activationGroup 等字段
        // When saveDecisionLog 被调用
        // Then DecisionRuleLog 的 ruleType 为 MATCHED 或 FIRED，各字段正确映射
        @Test
        @DisplayName("MATCHED 和 FIRED 规则的 ruleType 和字段正确")
        void shouldMapRuleFieldsCorrectly() {
            // Given
            RuleInfo matchedRule = buildRuleInfo("matched-rule", 100, "actGrp1", "agendaGrp1", "rfGrp1");
            RuleInfo firedRule = buildRuleInfo("fired-rule", 200, "actGrp2", "agendaGrp2", "rfGrp2");
            ExecutionResponseImpl response = buildResponse(
                    List.of(matchedRule), List.of(firedRule));

            when(flowLogMapper.insert(any(DecisionFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, DecisionFlowLog.class).setId(600L);
                return 1;
            });
            when(sqlSessionFactory.openSession(ExecutorType.BATCH, false)).thenReturn(sqlSession);
            when(sqlSession.getMapper(DecisionRuleLogMapper.class)).thenReturn(batchRuleMapper);

            // When
            service.saveDecisionLog(
                    USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", null, null,
                    Map.of(), Map.of(), Map.of(),
                    response, null,
                    10L, 20L, 5L, 3L, 50L, 100L, 10, null, null);

            // Then — 捕获所有写入的 DecisionRuleLog
            ArgumentCaptor<DecisionRuleLog> captor = ArgumentCaptor.forClass(DecisionRuleLog.class);
            verify(batchRuleMapper, times(2)).insert(captor.capture());

            List<DecisionRuleLog> inserted = captor.getAllValues();

            // MATCHED 规则
            DecisionRuleLog matchedLog = inserted.get(0);
            assertThat(matchedLog.getFlowLogId()).isEqualTo(600L);
            assertThat(matchedLog.getUserId()).isEqualTo(USER_ID);
            assertThat(matchedLog.getRuleType()).isEqualTo("MATCHED");
            assertThat(matchedLog.getRuleName()).isEqualTo("matched-rule");
            assertThat(matchedLog.getSalience()).isEqualTo(100);
            assertThat(matchedLog.getActivationGroup()).isEqualTo("actGrp1");
            assertThat(matchedLog.getAgendaGroup()).isEqualTo("agendaGrp1");
            assertThat(matchedLog.getRuleflowGroup()).isEqualTo("rfGrp1");
            assertThat(matchedLog.getRuleNodeIndex()).isEqualTo(0);
            assertThat(matchedLog.getRuleIndex()).isEqualTo(0);
            assertThat(matchedLog.getCreatedAt()).isNotNull();

            // FIRED 规则
            DecisionRuleLog firedLog = inserted.get(1);
            assertThat(firedLog.getRuleType()).isEqualTo("FIRED");
            assertThat(firedLog.getRuleName()).isEqualTo("fired-rule");
            assertThat(firedLog.getSalience()).isEqualTo(200);
            assertThat(firedLog.getActivationGroup()).isEqualTo("actGrp2");
        }
    }

    @Nested
    @DisplayName("Scenario: 主流水和参数正常保存")
    class FlowLogAndParamsSaved {

        // Given 有效的执行响应和参数
        // When saveDecisionLog 被调用
        // Then flowLogMapper.insert 和 flowParamsMapper.insert 各调用一次，字段正确
        @Test
        @DisplayName("主流水和参数各插入一次")
        void shouldInsertFlowLogAndParams() {
            // Given
            ExecutionResponseImpl response = new ExecutionResponseImpl();
            response.setDuration(123L);

            when(flowLogMapper.insert(any(DecisionFlowLog.class))).thenAnswer(inv -> {
                inv.getArgument(0, DecisionFlowLog.class).setId(700L);
                return 1;
            });

            // When
            Long result = service.saveDecisionLog(
                    USER_ID, ORDER_NO, FLOW_ID, "v1", "/pkg", "1.0",
                    "SUCCESS", "bad credit", "REJECT_001",
                    Map.of("age", 25), Map.of("score", 80), Map.of("entity", "data"),
                    response, null,
                    10L, 20L, 5L, 3L, 50L, 100L, 10, null, null);

            // Then
            assertThat(result).isEqualTo(700L);

            // 主流水
            ArgumentCaptor<DecisionFlowLog> flowCaptor = ArgumentCaptor.forClass(DecisionFlowLog.class);
            verify(flowLogMapper).insert(flowCaptor.capture());
            DecisionFlowLog flowLog = flowCaptor.getValue();
            assertThat(flowLog.getUserId()).isEqualTo(USER_ID);
            assertThat(flowLog.getOrderNo()).isEqualTo(ORDER_NO);
            assertThat(flowLog.getFlowId()).isEqualTo(FLOW_ID);
            assertThat(flowLog.getFlowVersion()).isEqualTo("v1");
            assertThat(flowLog.getExecutionStatus()).isEqualTo("SUCCESS");
            assertThat(flowLog.getRejectReason()).isEqualTo("bad credit");
            assertThat(flowLog.getRejectCode()).isEqualTo("REJECT_001");
            assertThat(flowLog.getExecutionTimeMs()).isEqualTo(123L);
            assertThat(flowLog.getTotalTimeMs()).isEqualTo(100L);

            // 参数
            ArgumentCaptor<DecisionFlowParams> paramsCaptor = ArgumentCaptor.forClass(DecisionFlowParams.class);
            verify(flowParamsMapper).insert(paramsCaptor.capture());
            DecisionFlowParams params = paramsCaptor.getValue();
            assertThat(params.getFlowLogId()).isEqualTo(700L);
            assertThat(params.getUserId()).isEqualTo(USER_ID);
            assertThat(params.getInputParams()).contains("\"age\"");
            assertThat(params.getOutputParams()).contains("\"score\"");
        }
    }
}
