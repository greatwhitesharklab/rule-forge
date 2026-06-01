package com.ruleforge.console.app.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.entity.BatchTestSessionEntity;
import com.ruleforge.console.app.mapper.BatchTestRowMapper;
import com.ruleforge.console.app.mapper.BatchTestSessionMapper;
import com.ruleforge.console.service.TestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: 批量测试进度查询
 *
 * BatchTestService 提供异步执行和进度查询。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BatchTestServiceImpl - 批量测试异步执行")
class BatchTestServiceImplTest {

    @Mock private BatchTestSessionMapper sessionMapper;
    @Mock private BatchTestRowMapper rowMapper;
    @Mock private TestService testService;
    @Mock private ObjectMapper objectMapper;
    @Mock(name = "batchTestExecutor")
    private Executor batchTestExecutor;
    @InjectMocks private BatchTestServiceImpl service;

    @Nested
    @DisplayName("Scenario: 查询不存在的会话进度")
    class SessionNotFound {

        // Given sessionId 不存在
        // When getSessionProgress 被调用
        // Then 返回 status=NOT_FOUND
        @Test
        @DisplayName("sessionId 不存在时返回 NOT_FOUND")
        void shouldReturnNotFoundForMissingSession() {
            when(sessionMapper.selectById(999L)).thenReturn(null);

            Map<String, Object> progress = service.getSessionProgress(999L);

            assertThat(progress.get("status")).isEqualTo("NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("Scenario: 查询运行中会话进度")
    class RunningSessionProgress {

        // Given session 存在且状态为 RUNNING
        // When getSessionProgress 被调用
        // Then 返回正确的进度信息
        @Test
        @DisplayName("返回 RUNNING 状态和进度")
        void shouldReturnRunningProgress() {
            // Given
            BatchTestSessionEntity session = new BatchTestSessionEntity();
            session.setId(1L);
            session.setStatus(BatchTestSessionEntity.STATUS_RUNNING);
            session.setTotalRows(100);
            session.setProgress(0.5);
            session.setErrorCount(2);
            when(sessionMapper.selectById(1L)).thenReturn(session);

            // countByStatus 返回的 map 中 cnt 是 Number
            Map<String, Object> successCount = new HashMap<>();
            successCount.put("status", "SUCCESS");
            successCount.put("cnt", 48L);
            Map<String, Object> errorCount = new HashMap<>();
            errorCount.put("status", "ERROR");
            errorCount.put("cnt", 2L);
            when(rowMapper.countByStatus(1L)).thenReturn(List.of(successCount, errorCount));

            // When
            Map<String, Object> progress = service.getSessionProgress(1L);

            // Then
            assertThat(progress.get("sessionId")).isEqualTo(1L);
            assertThat(progress.get("status")).isEqualTo("RUNNING");
            assertThat(progress.get("totalRows")).isEqualTo(100);
            assertThat(progress.get("progress")).isEqualTo(0.5);
            assertThat(progress.get("errorCount")).isEqualTo(2L);
            assertThat(progress.get("successCount")).isEqualTo(48L);
        }
    }

    @Nested
    @DisplayName("Scenario: 异步执行触发")
    class AsyncExecutionTrigger {

        // Given sessionId 和 KnowledgePackage
        // When executeBatchAsync 被调用
        // Then 任务被提交到 executor 并更新状态
        @Test
        @DisplayName("异步执行提交到线程池并更新状态")
        void shouldSubmitTaskToExecutor() {
            // Given
            when(rowMapper.selectBySessionId(1L)).thenReturn(List.of());
            when(sessionMapper.updateStatus(eq(1L), anyString(), anyDouble(), anyInt())).thenReturn(1);

            // 使用同步执行器方便测试
            Executor syncExecutor = Runnable::run;
            BatchTestServiceImpl syncService = new BatchTestServiceImpl(
                    sessionMapper, rowMapper, testService, objectMapper, syncExecutor);

            // When
            syncService.executeBatchAsync(1L, null, null, List.of());

            // Then — 验证状态先后更新为 RUNNING 和 COMPLETED
            verify(sessionMapper).updateStatus(eq(1L), eq("RUNNING"), anyDouble(), anyInt());
            verify(sessionMapper).updateStatus(eq(1L), eq("COMPLETED"), eq(1.0), anyInt());
        }
    }
}
