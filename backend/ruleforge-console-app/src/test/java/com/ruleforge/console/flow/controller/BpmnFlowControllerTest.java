package com.ruleforge.console.flow.controller;

import com.ruleforge.console.flow.converter.FlowXmlConverter;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.console.model.User;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: BPMN 流程 API
 */
@DisplayName("BpmnFlowController - BPMN 流程 REST API")
class BpmnFlowControllerTest {

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
    @DisplayName("加载 BPMN 文件")
    class LoadBpmn {

        @Test
        @DisplayName("正常加载已存在的 BPMN 文件")
        void shouldLoadExistingBpmnFile() throws Exception {
            // Given 仓库中存在文件 "project/flow.xml" 内容为 BPMN XML
            String bpmnContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><bpmn:process id=\"test\"/></bpmn:definitions>";
            InputStream is = new ByteArrayInputStream(bpmnContent.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.readFile("project/flow.xml", null)).thenReturn(is);

            // When GET /flow/load?file=project/flow.xml
            String result = controller.loadBpmn("project/flow.xml", null);

            // Then 应返回文件内容
            assertThat(result).contains("bpmn:definitions");
            assertThat(result).contains("bpmn:process");
        }

        @Test
        @DisplayName("文件不存在时应返回错误")
        void shouldReturnErrorWhenFileNotFound() throws Exception {
            // Given 仓库中不存在该文件
            when(repositoryService.readFile("notexist.xml", null))
                .thenThrow(new RuntimeException("File not found"));

            // When GET /flow/load?file=notexist.xml
            // Then 应抛出异常
            assertThatThrownBy(() -> controller.loadBpmn("notexist.xml", null))
                .isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("指定版本时应返回对应版本内容")
        void shouldLoadSpecificVersion() throws Exception {
            // Given 仓库中存在文件 "project/flow.xml" 的 v1.0 版本
            String versionContent = "<bpmn:definitions><bpmn:process id=\"v1\"/></bpmn:definitions>";
            InputStream is = new ByteArrayInputStream(versionContent.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.readFile("project/flow.xml", "v1.0")).thenReturn(is);

            // When GET /flow/load?file=project/flow.xml&version=v1.0
            String result = controller.loadBpmn("project/flow.xml", "v1.0");

            // Then 应返回指定版本的文件内容
            assertThat(result).contains("v1");
            verify(repositoryService).readFile("project/flow.xml", "v1.0");
        }
    }

    @Nested
    @DisplayName("保存 BPMN 文件")
    class SaveBpmn {

        @Test
        @DisplayName("正常保存 BPMN 文件")
        void shouldSaveBpmnFile() throws Exception {
            // Given 用户已登录
            String content = "<bpmn:definitions><bpmn:process id=\"test\"/></bpmn:definitions>";

            // When POST /flow/save file="flow.xml" content="..." newVersion=false
            String result = controller.saveBpmn("flow.xml", content, false);

            // Then 应调用 repositoryService.saveFile 保存
            verify(repositoryService).saveFile(eq("flow.xml"), eq(content), eq(false), isNull(), any(User.class));
            // And 返回 {"result":true}
            assertThat(result).isEqualTo("{\"result\":true}");
        }

        @Test
        @DisplayName("保存新版本")
        void shouldSaveNewVersion() throws Exception {
            // Given 用户已登录
            String content = "<bpmn:definitions/>";

            // When POST /flow/save file="flow.xml" content="..." newVersion=true
            String result = controller.saveBpmn("flow.xml", content, true);

            // Then 应调用 saveFile 并传入 newVersion=true
            verify(repositoryService).saveFile(eq("flow.xml"), eq(content), eq(true), isNull(), any(User.class));
            assertThat(result).isEqualTo("{\"result\":true}");
        }
    }

    @Nested
    @DisplayName("部署到 Flowable")
    class DeployBpmn {

        @Test
        @DisplayName("部署 BPMN 文件到 Flowable 引擎")
        void shouldDeployBpmnToFlowable() throws Exception {
            // Given 仓库中存在 BPMN 文件
            String bpmnContent = "<bpmn:definitions><bpmn:process id=\"test\"/></bpmn:definitions>";
            InputStream is = new ByteArrayInputStream(bpmnContent.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.readFile("flow.xml", null)).thenReturn(is);

            DeploymentBuilder builder = mock(DeploymentBuilder.class);
            when(flowableRepositoryService.createDeployment()).thenReturn(builder);
            when(builder.addString(anyString(), anyString())).thenReturn(builder);
            when(builder.name(anyString())).thenReturn(builder);
            Deployment deployment = mock(Deployment.class);
            when(deployment.getId()).thenReturn("deploy-123");
            when(builder.deploy()).thenReturn(deployment);

            // When POST /flow/deploy file="flow.xml"
            String result = controller.deployBpmn("flow.xml", null);

            // Then 应通过 Flowable RepositoryService 创建部署
            verify(flowableRepositoryService).createDeployment();
            verify(builder).deploy();
            // And 返回 deploymentId
            assertThat(result).contains("deploy-123");
        }

        @Test
        @DisplayName("部署指定版本")
        void shouldDeploySpecificVersion() throws Exception {
            // Given 仓库中存在 BPMN 文件的指定版本
            String bpmnContent = "<bpmn:definitions><bpmn:process id=\"v1\"/></bpmn:definitions>";
            InputStream is = new ByteArrayInputStream(bpmnContent.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.readFile("flow.xml", "v1.0")).thenReturn(is);

            DeploymentBuilder builder = mock(DeploymentBuilder.class);
            when(flowableRepositoryService.createDeployment()).thenReturn(builder);
            when(builder.addString(anyString(), anyString())).thenReturn(builder);
            when(builder.name(anyString())).thenReturn(builder);
            Deployment deployment = mock(Deployment.class);
            when(deployment.getId()).thenReturn("deploy-v1");
            when(builder.deploy()).thenReturn(deployment);

            // When POST /flow/deploy file="flow.xml" version="v1.0"
            String result = controller.deployBpmn("flow.xml", "v1.0");

            // Then 应部署指定版本的文件
            verify(repositoryService).readFile("flow.xml", "v1.0");
            assertThat(result).contains("deploy-v1");
        }
    }

    @Nested
    @DisplayName("转换旧格式 XML")
    class ConvertBpmn {

        @Test
        @DisplayName("转换旧格式 rule-flow XML 为 BPMN 2.0")
        void shouldConvertOldXmlToBpmn() throws Exception {
            // Given 仓库中存在旧格式 <rule-flow> 文件
            String oldXml = "<rule-flow id=\"test-flow\"><start name=\"S1\"/><end name=\"E1\"/></rule-flow>";
            InputStream is = new ByteArrayInputStream(oldXml.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.readFile("old-flow.xml", null)).thenReturn(is);

            // When POST /flow/convert file="old-flow.xml"
            String result = controller.convertToBpmn("old-flow.xml");

            // Then 应返回转换后的 BPMN 2.0 XML，包含 bpmn:definitions 根元素
            assertThat(result).contains("bpmn:definitions");
            assertThat(result).contains("bpmn:startEvent");
            assertThat(result).contains("bpmn:endEvent");
        }

        @Test
        @DisplayName("文件不存在时应返回错误")
        void shouldReturnErrorWhenConvertFileNotFound() throws Exception {
            // Given 仓库中不存在该文件
            when(repositoryService.readFile("notexist.xml", null))
                .thenThrow(new RuntimeException("File not found"));

            // When POST /flow/convert file="notexist.xml"
            // Then 应抛出异常
            assertThatThrownBy(() -> controller.convertToBpmn("notexist.xml"))
                .isInstanceOf(Exception.class);
        }
    }
}
