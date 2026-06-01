package com.ruleforge.console.flow.controller;

import com.ruleforge.console.flow.converter.FlowXmlConverter;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.console.model.User;
import com.ruleforge.exception.RuleException;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: BPMN 流程 API Bug 回归测试
 *
 * 覆盖 BUG-1 来自 2026-05-28 决策流 walkthrough：
 * 新创建的流程文件加载时返回 500 而非 404。
 */
@DisplayName("BpmnFlowController Bug 回归测试")
class BpmnFlowControllerBugRegressionTest {

    private RuleForgeRepositoryService repositoryService;
    private RepositoryService flowableRepositoryService;
    private FlowXmlConverter flowXmlConverter;
    private BpmnFlowController controller;
    private MockedStatic<EnvironmentUtils> envUtilsMock;

    @BeforeEach
    void setUp() {
        repositoryService = mock(RuleForgeRepositoryService.class);
        flowableRepositoryService = mock(RepositoryService.class);
        flowXmlConverter = new FlowXmlConverter();
        controller = new BpmnFlowController(repositoryService, flowableRepositoryService, flowXmlConverter);
        envUtilsMock = mockStatic(EnvironmentUtils.class);
        User testUser = mock(User.class);
        when(testUser.getUsername()).thenReturn("testuser");
        envUtilsMock.when(() -> EnvironmentUtils.getLoginUser(null))
            .thenReturn(testUser);
    }

    @AfterEach
    void tearDown() {
        envUtilsMock.close();
    }

    @Nested
    @DisplayName("BUG-1: 新文件加载应返回 404 而非 500")
    class Bug1FlowLoadReturns404ForNewFiles {

        @Test
        @DisplayName("加载新创建的空流程文件时应返回 404 而非 500")
        void shouldReturn404ForNewlyCreatedFlowFile() throws Exception {
            // Given 仓库中不存在文件 "project/new-flow.rl.xml"
            //     And repositoryService.readFile 抛出 RuleException("File [project/new-flow.rl.xml] not exist.")
            when(repositoryService.readFile("project/new-flow.rl.xml", null))
                .thenThrow(new RuleException("File [project/new-flow.rl.xml] not exist."));

            // When GET /flow/load?file=project/new-flow.rl.xml
            ResponseEntity<String> result = controller.loadBpmn("project/new-flow.rl.xml", null);

            // Then 应返回 HTTP 404 Not Found（非 500）
            assertThat(result.getStatusCode().value()).isEqualTo(404);
            // And body 为空
            assertThat(result.getBody()).isNull();
        }

        @Test
        @DisplayName("加载已存在的文件仍正常返回 200")
        void shouldReturn200ForExistingFlowFile() throws Exception {
            // Given 仓库中存在文件 "project/existing-flow.rl.xml"
            String bpmnContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><bpmn:process id=\"test\"/></bpmn:definitions>";
            InputStream is = new ByteArrayInputStream(bpmnContent.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.readFile("project/existing-flow.rl.xml", null)).thenReturn(is);

            // When GET /flow/load?file=project/existing-flow.rl.xml
            ResponseEntity<String> result = controller.loadBpmn("project/existing-flow.rl.xml", null);

            // Then 应返回 HTTP 200 和文件内容
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).contains("bpmn:definitions");
        }

        @Test
        @DisplayName("loadBpmn 返回类型为 ResponseEntity 允许区分 404 和 500")
        void shouldUseResponseEntityForLoadBpmnReturn() throws Exception {
            // Given 仓库返回 null（文件为空）
            when(repositoryService.readFile("empty.xml", null)).thenReturn(null);

            // When GET /flow/load?file=empty.xml
            ResponseEntity<String> result = controller.loadBpmn("empty.xml", null);

            // Then 应返回 404
            assertThat(result.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        @DisplayName("非文件不存在类型的异常仍应返回 500")
        void shouldReturn500ForNonFileNotFoundExceptions() throws Exception {
            // Given 仓库抛出非文件不存在的异常（如数据库连接失败）
            when(repositoryService.readFile("flow.xml", null))
                .thenThrow(new RuleException("Database connection failed"));

            // When GET /flow/load?file=flow.xml
            // Then 应抛出 RuleException → 映射为 500
            assertThatThrownBy(() -> controller.loadBpmn("flow.xml", null))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Database connection failed");
        }
    }
}
