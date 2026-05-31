package com.ruleforge.decision.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruleforge.decision.entity.*;
import com.ruleforge.decision.mapper.ShadowComparisonMapper;
import com.ruleforge.decision.repository.DecisionLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ShadowComparisonServiceImpl 单元测试
 *
 * BDD 行为场景:
 *
 * 1. Given 主流程和陪跑流程完全一致, When compareAndSave, Then has_divergence=false, severity=NONE
 * 2. Given 主流程 SUCCESS + 陪跑 FAILED, When compareAndSave, Then statusMatch=false, severity=HIGH
 * 3. Given 主流程通过(rejectCode=null) + 陪跑拒绝(rejectCode=R001), When compareAndSave, Then resultMatch=false, severity=HIGH
 * 4. Given 输出字段 creditLimit 不同(50000 vs 30000)但状态和结果一致, When compareAndSave, Then has_divergence=true, severity=MEDIUM
 * 5. Given 触发规则不同但最终结果一致, When compareAndSave, Then has_divergence=true, severity=LOW
 * 6. Given 陪跑日志尚未写入, When compareAndSave, Then 不抛异常, 不写入对比记录
 */
@ExtendWith(MockitoExtension.class)
class ShadowComparisonServiceImplTest {

    @Mock
    private DecisionLogRepository decisionLogRepository;

    @Mock
    private ShadowComparisonMapper shadowComparisonMapper;

    private ShadowComparisonServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShadowComparisonServiceImpl(decisionLogRepository, shadowComparisonMapper);
    }

    private DecisionFlowLog buildMainLog(String status, String rejectCode, long totalTimeMs) {
        DecisionFlowLog log = new DecisionFlowLog();
        log.setId(100L);
        log.setUserId("user1");
        log.setOrderNo("order1");
        log.setExecutionStatus(status);
        log.setRejectCode(rejectCode);
        log.setRejectReason(rejectCode != null ? "Rejected: " + rejectCode : null);
        log.setRulePackagePath("project/pkg1");
        log.setTotalTimeMs(totalTimeMs);
        return log;
    }

    private ShadowFlowLog buildShadowLog(String status, String rejectCode, long totalTimeMs) {
        ShadowFlowLog log = new ShadowFlowLog();
        log.setId(200L);
        log.setMainFlowLogId(100L);
        log.setUserId("user1");
        log.setOrderNo("order1");
        log.setExecutionStatus(status);
        log.setRejectCode(rejectCode);
        log.setRejectReason(rejectCode != null ? "Rejected: " + rejectCode : null);
        log.setRulePackagePath("project/pkg1-v2");
        log.setTotalTimeMs(totalTimeMs);
        return log;
    }

    private DecisionFlowParams buildMainParams(String outputJson) {
        DecisionFlowParams params = new DecisionFlowParams();
        params.setFlowLogId(100L);
        params.setOutputParams(outputJson);
        return params;
    }

    private ShadowFlowParams buildShadowParams(String outputJson) {
        ShadowFlowParams params = new ShadowFlowParams();
        params.setFlowLogId(200L);
        params.setOutputParams(outputJson);
        return params;
    }

    // ===== 场景 1: 完全一致 =====

    @Nested
    @DisplayName("主流程和陪跑流程完全一致")
    class AllMatch {

        @Test
        @DisplayName("Given 主+陪跑 executionStatus=SUCCESS, rejectCode=null, outputParams 一致, When compareAndSave, Then has_divergence=false, severity=NONE")
        void allMatch_noDivergence() {
            DecisionFlowLog mainLog = buildMainLog("SUCCESS", null, 100);
            ShadowFlowLog shadowLog = buildShadowLog("SUCCESS", null, 120);
            String outputJson = "{\"ruleResult\":\"PASS\",\"creditLimit\":\"50000\"}";

            when(decisionLogRepository.findFlowLogById(100L)).thenReturn(mainLog);
            when(decisionLogRepository.findShadowFlowLogByMainFlowLogId(100L)).thenReturn(shadowLog);
            when(decisionLogRepository.findFlowParamsByFlowLogId(100L)).thenReturn(buildMainParams(outputJson));
            when(decisionLogRepository.findShadowFlowParamsByFlowLogId(200L)).thenReturn(buildShadowParams(outputJson));
            when(decisionLogRepository.findRuleLogsByFlowLogId(100L)).thenReturn(Collections.emptyList());
            when(decisionLogRepository.findShadowRuleLogsByFlowLogId(200L)).thenReturn(Collections.emptyList());

            service.compareAndSave(100L, 200L);

            ArgumentCaptor<ShadowComparison> captor = ArgumentCaptor.forClass(ShadowComparison.class);
            verify(shadowComparisonMapper).insert(captor.capture());

            ShadowComparison result = captor.getValue();
            assertTrue(result.getStatusMatch());
            assertTrue(result.getResultMatch());
            assertFalse(result.getHasDivergence());
            assertEquals("NONE", result.getDivergenceSeverity());
        }
    }

    // ===== 场景 2: 执行状态不一致 =====

    @Nested
    @DisplayName("执行状态不一致")
    class StatusMismatch {

        @Test
        @DisplayName("Given 主 SUCCESS + 陪跑 FAILED, When compareAndSave, Then statusMatch=false, severity=HIGH")
        void statusMismatch_highSeverity() {
            DecisionFlowLog mainLog = buildMainLog("SUCCESS", null, 100);
            ShadowFlowLog shadowLog = buildShadowLog("FAILED", null, 150);

            when(decisionLogRepository.findFlowLogById(100L)).thenReturn(mainLog);
            when(decisionLogRepository.findShadowFlowLogByMainFlowLogId(100L)).thenReturn(shadowLog);
            when(decisionLogRepository.findFlowParamsByFlowLogId(100L)).thenReturn(buildMainParams("{}"));
            when(decisionLogRepository.findShadowFlowParamsByFlowLogId(200L)).thenReturn(buildShadowParams("{}"));
            when(decisionLogRepository.findRuleLogsByFlowLogId(100L)).thenReturn(Collections.emptyList());
            when(decisionLogRepository.findShadowRuleLogsByFlowLogId(200L)).thenReturn(Collections.emptyList());

            service.compareAndSave(100L, 200L);

            ArgumentCaptor<ShadowComparison> captor = ArgumentCaptor.forClass(ShadowComparison.class);
            verify(shadowComparisonMapper).insert(captor.capture());

            ShadowComparison result = captor.getValue();
            assertFalse(result.getStatusMatch());
            assertTrue(result.getHasDivergence());
            assertEquals("HIGH", result.getDivergenceSeverity());
            assertEquals("SUCCESS", result.getMainExecutionStatus());
            assertEquals("FAILED", result.getShadowExecutionStatus());
        }
    }

    // ===== 场景 3: 决策结果不一致 =====

    @Nested
    @DisplayName("决策结果不一致（不同 rejectCode）")
    class ResultMismatch {

        @Test
        @DisplayName("Given 主通过(rejectCode=null) + 陪跑拒绝(rejectCode=R001), When compareAndSave, Then resultMatch=false, severity=HIGH")
        void resultMismatch_highSeverity() {
            DecisionFlowLog mainLog = buildMainLog("SUCCESS", null, 100);
            ShadowFlowLog shadowLog = buildShadowLog("SUCCESS", "R001", 120);
            String outputJson = "{\"ruleResult\":\"PASS\"}";

            when(decisionLogRepository.findFlowLogById(100L)).thenReturn(mainLog);
            when(decisionLogRepository.findShadowFlowLogByMainFlowLogId(100L)).thenReturn(shadowLog);
            when(decisionLogRepository.findFlowParamsByFlowLogId(100L)).thenReturn(buildMainParams(outputJson));
            when(decisionLogRepository.findShadowFlowParamsByFlowLogId(200L)).thenReturn(buildShadowParams(outputJson));
            when(decisionLogRepository.findRuleLogsByFlowLogId(100L)).thenReturn(Collections.emptyList());
            when(decisionLogRepository.findShadowRuleLogsByFlowLogId(200L)).thenReturn(Collections.emptyList());

            service.compareAndSave(100L, 200L);

            ArgumentCaptor<ShadowComparison> captor = ArgumentCaptor.forClass(ShadowComparison.class);
            verify(shadowComparisonMapper).insert(captor.capture());

            ShadowComparison result = captor.getValue();
            assertTrue(result.getStatusMatch()); // 状态都是 SUCCESS
            assertFalse(result.getResultMatch()); // 但拒绝码不同
            assertTrue(result.getHasDivergence());
            assertEquals("HIGH", result.getDivergenceSeverity());
            assertNull(result.getMainRejectCode());
            assertEquals("R001", result.getShadowRejectCode());
        }
    }

    // ===== 场景 4: 输出字段不一致 =====

    @Nested
    @DisplayName("输出字段部分不一致")
    class OutputFieldMismatch {

        @Test
        @DisplayName("Given creditLimit 不同(50000 vs 30000)但状态和结果一致, When compareAndSave, Then severity=MEDIUM, output_divergence 有差异")
        void outputMismatch_mediumSeverity() {
            DecisionFlowLog mainLog = buildMainLog("SUCCESS", null, 100);
            ShadowFlowLog shadowLog = buildShadowLog("SUCCESS", null, 120);
            String mainOutput = "{\"ruleResult\":\"PASS\",\"creditLimit\":\"50000\",\"lockDays\":\"30\"}";
            String shadowOutput = "{\"ruleResult\":\"PASS\",\"creditLimit\":\"30000\",\"lockDays\":\"30\"}";

            when(decisionLogRepository.findFlowLogById(100L)).thenReturn(mainLog);
            when(decisionLogRepository.findShadowFlowLogByMainFlowLogId(100L)).thenReturn(shadowLog);
            when(decisionLogRepository.findFlowParamsByFlowLogId(100L)).thenReturn(buildMainParams(mainOutput));
            when(decisionLogRepository.findShadowFlowParamsByFlowLogId(200L)).thenReturn(buildShadowParams(shadowOutput));
            when(decisionLogRepository.findRuleLogsByFlowLogId(100L)).thenReturn(Collections.emptyList());
            when(decisionLogRepository.findShadowRuleLogsByFlowLogId(200L)).thenReturn(Collections.emptyList());

            service.compareAndSave(100L, 200L);

            ArgumentCaptor<ShadowComparison> captor = ArgumentCaptor.forClass(ShadowComparison.class);
            verify(shadowComparisonMapper).insert(captor.capture());

            ShadowComparison result = captor.getValue();
            assertTrue(result.getStatusMatch());
            assertTrue(result.getResultMatch());
            assertTrue(result.getHasDivergence());
            assertEquals("MEDIUM", result.getDivergenceSeverity());
            assertNotNull(result.getOutputDivergence());
            assertTrue(result.getOutputDivergence().contains("creditLimit"));
        }
    }

    // ===== 场景 5: 规则执行不一致 =====

    @Nested
    @DisplayName("规则执行差异（最终结果一致）")
    class RuleExecutionMismatch {

        @Test
        @DisplayName("Given 主触发 [ruleA,ruleB,ruleC] + 陪跑触发 [ruleA,ruleB,ruleD] + 结果一致, When compareAndSave, Then severity=LOW, rule_divergence 有差异")
        void ruleMismatch_lowSeverity() {
            DecisionFlowLog mainLog = buildMainLog("SUCCESS", null, 100);
            ShadowFlowLog shadowLog = buildShadowLog("SUCCESS", null, 120);
            String outputJson = "{\"ruleResult\":\"PASS\"}";

            DecisionRuleLog mainRule1 = new DecisionRuleLog();
            mainRule1.setRuleName("ruleA");
            DecisionRuleLog mainRule2 = new DecisionRuleLog();
            mainRule2.setRuleName("ruleB");
            DecisionRuleLog mainRule3 = new DecisionRuleLog();
            mainRule3.setRuleName("ruleC");

            ShadowRuleLog shadowRule1 = new ShadowRuleLog();
            shadowRule1.setRuleName("ruleA");
            ShadowRuleLog shadowRule2 = new ShadowRuleLog();
            shadowRule2.setRuleName("ruleB");
            ShadowRuleLog shadowRule3 = new ShadowRuleLog();
            shadowRule3.setRuleName("ruleD");

            when(decisionLogRepository.findFlowLogById(100L)).thenReturn(mainLog);
            when(decisionLogRepository.findShadowFlowLogByMainFlowLogId(100L)).thenReturn(shadowLog);
            when(decisionLogRepository.findFlowParamsByFlowLogId(100L)).thenReturn(buildMainParams(outputJson));
            when(decisionLogRepository.findShadowFlowParamsByFlowLogId(200L)).thenReturn(buildShadowParams(outputJson));
            when(decisionLogRepository.findRuleLogsByFlowLogId(100L)).thenReturn(List.of(mainRule1, mainRule2, mainRule3));
            when(decisionLogRepository.findShadowRuleLogsByFlowLogId(200L)).thenReturn(List.of(shadowRule1, shadowRule2, shadowRule3));

            service.compareAndSave(100L, 200L);

            ArgumentCaptor<ShadowComparison> captor = ArgumentCaptor.forClass(ShadowComparison.class);
            verify(shadowComparisonMapper).insert(captor.capture());

            ShadowComparison result = captor.getValue();
            assertTrue(result.getStatusMatch());
            assertTrue(result.getResultMatch());
            assertTrue(result.getHasDivergence());
            assertEquals("LOW", result.getDivergenceSeverity());
            assertNotNull(result.getRuleDivergence());
            assertTrue(result.getRuleDivergence().contains("ruleC")); // only in main
            assertTrue(result.getRuleDivergence().contains("ruleD")); // only in shadow
        }
    }

    // ===== 场景 6: 陪跑日志不存在 =====

    @Nested
    @DisplayName("陪跑日志不存在")
    class ShadowLogNotFound {

        @Test
        @DisplayName("Given mainFlowLogId 对应的 shadowFlowLog 不存在, When compareAndSave, Then 不抛异常, 不写入对比记录")
        void shadowLogNotFound_noError() {
            when(decisionLogRepository.findFlowLogById(100L)).thenReturn(buildMainLog("SUCCESS", null, 100));
            when(decisionLogRepository.findShadowFlowLogByMainFlowLogId(100L)).thenReturn(null);

            assertDoesNotThrow(() -> service.compareAndSave(100L, 200L));
            verify(shadowComparisonMapper, never()).insert(any(ShadowComparison.class));
        }

        @Test
        @DisplayName("Given mainFlowLogId 对应的主日志不存在, When compareAndSave, Then 不抛异常, 不写入对比记录")
        void mainLogNotFound_noError() {
            when(decisionLogRepository.findFlowLogById(999L)).thenReturn(null);

            assertDoesNotThrow(() -> service.compareAndSave(999L, 200L));
            verify(shadowComparisonMapper, never()).insert(any(ShadowComparison.class));
        }
    }
}
