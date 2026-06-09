package com.ruleforge.console.batchtest.impl;

import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.batchtest.SubjectExecutionContext;
import com.ruleforge.console.batchtest.SubjectResult;
import com.ruleforge.console.model.ApplicationAllVariableCategoryMap;
import com.ruleforge.console.model.BatchTestFlowMap;
import com.ruleforge.console.model.SaveProcessItemDto;
import com.ruleforge.console.service.TestService;
import com.ruleforge.exception.RuleException;
import com.ruleforge.runtime.KnowledgePackage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Feature: FlowBatchTestSubject — FLOW mode subject (V5.8.0)
 *
 * 行为约定:
 *   - 跑 testService.doFlowTest，成功返 SubjectResult.success(...)
 *   - RuleException 捕获,errorCode = label，errorMessage = String.valueOf(val)
 *   - 其他 Exception 捕获，errorCode = "INTERNAL_ERROR"
 *   - getType() 返 BatchTestSessionEntity.SUBJECT_FLOW
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlowBatchTestSubject - FLOW 模式 subject (V5.8.0)")
class FlowBatchTestSubjectTest {

    @Mock private TestService testService;
    @Mock private KnowledgePackage knowledgePackage;
    @InjectMocks private FlowBatchTestSubject subject;

    private SubjectExecutionContext buildCtx() {
        Map<String, Object> params = new HashMap<>();
        params.put("flowId", "f");
        params.put("knowledgePackage", knowledgePackage);
        params.put("flowMap", new BatchTestFlowMap());
        return new SubjectExecutionContext(
                1L, 100L, new ApplicationAllVariableCategoryMap(), params);
    }

    @Nested
    @DisplayName("Scenario: 成功跑流")
    class SuccessfulFlow {

        // Given subject.execute(ctx) with valid flow + valid input row
        // When testService.doFlowTest returns a non-null SaveProcessItemDto
        // Then SubjectResult.isSuccess() == true, latencyMs >= 0
        @Test
        @DisplayName("成功跑流 — SubjectResult.success 含 output")
        void shouldReturnSuccessResult() throws Exception {
            SaveProcessItemDto dto = new SaveProcessItemDto();
            when(testService.doFlowTest(
                    eq(knowledgePackage),
                    eq("f"),
                    any(ApplicationAllVariableCategoryMap.class),
                    any(BatchTestFlowMap.class))).thenReturn(dto);

            SubjectResult result = subject.execute(buildCtx());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).isSameAs(dto);
            assertThat(result.errorCode()).isNull();
            assertThat(result.errorMessage()).isNull();
            assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Scenario: 规则异常 → SubjectResult.failure with errorCode = label")
    class RuleExceptionFailure {

        // Given subject.execute(ctx) where testService.doFlowTest throws
        //       RuleException("BAD_INPUT", "invalid value", "msg", null)
        // Then SubjectResult.isSuccess() == false
        // And errorCode == "BAD_INPUT"
        // And errorMessage == "invalid value"
        @Test
        @DisplayName("RuleException 映射为 failure(errorCode = label, errorMessage = val)")
        void shouldMapRuleExceptionToFailure() throws Exception {
            when(testService.doFlowTest(any(), any(), any(), any()))
                    .thenThrow(new RuleException("BAD_INPUT", "invalid value", "tip", null));

            SubjectResult result = subject.execute(buildCtx());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.errorCode()).isEqualTo("BAD_INPUT");
            assertThat(result.errorMessage()).isEqualTo("invalid value");
            assertThat(result.output()).isNull();
            assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Scenario: 未知异常 → SubjectResult.failure with INTERNAL_ERROR")
    class GenericExceptionFailure {

        // Given subject.execute(ctx) where testService.doFlowTest throws RuntimeException("oops")
        // Then errorCode == "INTERNAL_ERROR"
        @Test
        @DisplayName("非 RuleException 映射为 INTERNAL_ERROR")
        void shouldMapGenericExceptionToInternalError() throws Exception {
            when(testService.doFlowTest(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("oops"));

            SubjectResult result = subject.execute(buildCtx());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.errorCode()).isEqualTo("INTERNAL_ERROR");
            assertThat(result.errorMessage()).isEqualTo("oops");
            assertThat(result.output()).isNull();
        }
    }

    @Nested
    @DisplayName("Scenario: getType 返 FLOW")
    class GetType {

        // When new FlowBatchTestSubject(mockTestService).getType()
        // Then result == BatchTestSessionEntity.SUBJECT_FLOW
        @Test
        @DisplayName("getType() == BatchTestSessionEntity.SUBJECT_FLOW")
        void shouldReturnFlowType() {
            assertThat(subject.getType()).isEqualTo(BatchTestSessionEntity.SUBJECT_FLOW);
            assertThat(subject.getType()).isEqualTo("FLOW");
        }
    }
}
