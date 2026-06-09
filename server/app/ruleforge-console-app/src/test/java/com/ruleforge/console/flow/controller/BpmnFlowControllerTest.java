package com.ruleforge.console.flow.controller;

import com.ruleforge.console.flow.converter.FlowXmlConverter;
import com.ruleforge.console.service.RuleForgeRepositoryService;
import com.ruleforge.console.util.EnvironmentUtils;
import com.ruleforge.console.model.User;
import com.ruleforge.decision.flow.parser.BpmnXmlParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

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
    private FlowXmlConverter flowXmlConverter;
    private BpmnFlowController controller;
    private MockedStatic<EnvironmentUtils> envUtilsMock;

    @BeforeEach
    void setUp() {
        repositoryService = mock(RuleForgeRepositoryService.class);
        flowXmlConverter = new FlowXmlConverter();
        BpmnXmlParser parser = new BpmnXmlParser();
        RestTemplate restTemplate = mock(RestTemplate.class);
        // V5.21+: 构造器去 flowableRepositoryService(deployBpmn 改为走自建路径)
        controller = new BpmnFlowController(repositoryService, flowXmlConverter, parser, restTemplate);
        // V5.20+: 注入 ruleforge.exec.url(原本 @Value 拿,这里测试用 ReflectionTestUtils 注入)
        ReflectionTestUtils.setField(controller, "execUrl", "http://test-exec:8280");
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

        // ──────────────────────────────────────────────────
        // BDD STUB: V5.9.x NPE regression — null InputStream from readFile
        //
        // Given  Git 存储层不可用,readFile 走 DB fallback,DB 也没 file_content
        //        (生产: saveFile 把 content 存 Git 不存 DB,只在容器没启用 Git 存储时 NPE)
        // When   GET /flow/load?file=test_rl.xml
        // Then   应返回 404 (file not found),不应抛 NullPointerException
        //
        // 当前 controller 行为: inputStream.readAllBytes() 在 inputStream=null 时 NPE
        // 抛 RuleException → 走 GlobalExceptionHandler → 400 纯文本 body
        // 修复方向: 在 inputStream==null 时显式 return notFound() (跟 notFound.xml 路径一致)
        @Test
        @DisplayName("【V5.9.x BUG】readFile 返 null (Git 不可用 + DB 没 content) 不应 NPE")
        void shouldReturn404WhenReadFileReturnsNull() throws Exception {
            // Given Git 存储不可用 + DB 也没 file_content, readFile 静默返 null
            when(repositoryService.readFile("git_unavailable.xml", null)).thenReturn(null);

            // When GET /flow/load?file=git_unavailable.xml
            ResponseEntity<String> result = controller.loadBpmn("git_unavailable.xml", null);

            // Then 应返 404 (跟 notexist.xml 路径一致), 不应 NPE
            assertThat(result.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        @DisplayName("正常加载已存在的 BPMN 文件")
        void shouldLoadExistingBpmnFile() throws Exception {
            // Given 仓库中存在文件 "project/flow.xml" 内容为 BPMN XML
            String bpmnContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><bpmn:process id=\"test\"/></bpmn:definitions>";
            InputStream is = new ByteArrayInputStream(bpmnContent.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.readFile("project/flow.xml", null)).thenReturn(is);

            // When GET /flow/load?file=project/flow.xml
            ResponseEntity<String> result = controller.loadBpmn("project/flow.xml", null);

            // Then 应返回 200 和文件内容
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).contains("bpmn:definitions");
            assertThat(result.getBody()).contains("bpmn:process");
        }

        @Test
        @DisplayName("文件不存在时应返回 404")
        void shouldReturn404WhenFileNotFound() throws Exception {
            // Given 仓库中不存在该文件
            when(repositoryService.readFile("notexist.xml", null))
                .thenThrow(new RuntimeException("File [notexist.xml] not exist."));

            // When GET /flow/load?file=notexist.xml
            ResponseEntity<String> result = controller.loadBpmn("notexist.xml", null);

            // Then 应返回 404
            assertThat(result.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        @DisplayName("指定版本时应返回对应版本内容")
        void shouldLoadSpecificVersion() throws Exception {
            // Given 仓库中存在文件 "project/flow.xml" 的 v1.0 版本
            String versionContent = "<bpmn:definitions><bpmn:process id=\"v1\"/></bpmn:definitions>";
            InputStream is = new ByteArrayInputStream(versionContent.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.readFile("project/flow.xml", "v1.0")).thenReturn(is);

            // When GET /flow/load?file=project/flow.xml&version=v1.0
            ResponseEntity<String> result = controller.loadBpmn("project/flow.xml", "v1.0");

            // Then 应返回指定版本的文件内容
            assertThat(result.getStatusCode().value()).isEqualTo(200);
            assertThat(result.getBody()).contains("v1");
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
    @DisplayName("部署 BPMN(V5.21+: 不再调 Flowable,改为 parse + invalidate)")
    class DeployBpmn {

        @Test
        @DisplayName("部署 BPMN 文件:解析 IR + 返回 file 名占位的 deploymentId")
        void shouldDeployBpmnToFlowEngine() throws Exception {
            // Given 仓库中存在 BPMN 文件(声明 xmlns:bpmn,BpmnXmlParser 校验格式)
            String bpmnContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><bpmn:process id=\"test\"><bpmn:startEvent id=\"start\"/><bpmn:endEvent id=\"end\"/></bpmn:process></bpmn:definitions>";
            InputStream is = new ByteArrayInputStream(bpmnContent.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.readFile("flow.xml", null)).thenReturn(is);

            // When POST /flow/deploy file="flow.xml"
            String result = controller.deployBpmn("flow.xml", null);

            // Then 返回 {"deploymentId":"flow.xml"} — file 名占位,前端 alert 成功
            assertThat(result).isEqualTo("{\"deploymentId\":\"flow.xml\"}");
            // And readFile 被调(读 BPMN XML 给 parser)
            verify(repositoryService).readFile("flow.xml", null);
        }

        @Test
        @DisplayName("部署指定版本")
        void shouldDeploySpecificVersion() throws Exception {
            // Given 仓库中存在 BPMN 文件的指定版本(声明 xmlns:bpmn,BpmnXmlParser 校验格式)
            String bpmnContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"><bpmn:process id=\"v1\"><bpmn:startEvent id=\"start\"/><bpmn:endEvent id=\"end\"/></bpmn:process></bpmn:definitions>";
            InputStream is = new ByteArrayInputStream(bpmnContent.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.readFile("flow.xml", "v1.0")).thenReturn(is);

            // When POST /flow/deploy file="flow.xml" version="v1.0"
            String result = controller.deployBpmn("flow.xml", "v1.0");

            // Then 应读指定版本 + 返回 file 名占位的 deploymentId
            verify(repositoryService).readFile("flow.xml", "v1.0");
            assertThat(result).isEqualTo("{\"deploymentId\":\"flow.xml\"}");
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
