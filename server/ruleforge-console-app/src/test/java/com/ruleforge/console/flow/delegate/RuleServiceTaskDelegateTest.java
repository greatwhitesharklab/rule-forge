package com.ruleforge.console.flow.delegate;

import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Feature: 规则节点执行
 */
@DisplayName("RuleServiceTaskDelegate - 规则节点执行")
class RuleServiceTaskDelegateTest {

    private static final String RF_NS = "http://ruleforge.com/schema";

    private RuleServiceTaskDelegate delegate;
    private MockedStatic<Utils> utilsMock;
    private MockedStatic<KnowledgeSessionFactory> factoryMock;
    private ApplicationContext applicationContext;
    private KnowledgeService knowledgeService;
    private KnowledgeBuilder knowledgeBuilder;

    @BeforeEach
    void setUp() {
        knowledgeBuilder = mock(KnowledgeBuilder.class);
        delegate = new RuleServiceTaskDelegate(knowledgeBuilder);
        utilsMock = mockStatic(Utils.class);
        factoryMock = mockStatic(KnowledgeSessionFactory.class);

        applicationContext = mock(ApplicationContext.class);
        knowledgeService = mock(KnowledgeService.class);
        utilsMock.when(Utils::getApplicationContext).thenReturn(applicationContext);
        when(applicationContext.getBean(KnowledgeService.BEAN_ID)).thenReturn(knowledgeService);
    }

    /**
     * Set up knowledgeBuilder mock to produce a working KnowledgePackage via buildFromFile path.
     */
    private void setupKnowledgeBuilder(String file, KnowledgePackage pkg) {
        ResourceBase resourceBase = mock(ResourceBase.class);
        KnowledgeBase knowledgeBase = mock(KnowledgeBase.class);
        when(knowledgeBuilder.newResourceBase()).thenReturn(resourceBase);
        when(knowledgeBuilder.buildKnowledgeBase(any(ResourceBase.class))).thenReturn(knowledgeBase);
        when(knowledgeBase.getKnowledgePackage()).thenReturn(pkg);
    }

    @AfterEach
    void tearDown() {
        utilsMock.close();
        factoryMock.close();
    }

    private void addExtensionAttr(ServiceTask task, String name, String value) {
        ExtensionAttribute attr = new ExtensionAttribute(name, value);
        attr.setNamespace(RF_NS);
        attr.setNamespacePrefix("ruleforge");
        task.addAttribute(attr);
    }

    private DelegateExecution createExecution(String file, String project, Map<String, Object> variables) {
        DelegateExecution execution = mock(DelegateExecution.class);
        ServiceTask task = new ServiceTask();
        if (file != null) {
            addExtensionAttr(task, "file", file);
        }
        if (project != null) {
            addExtensionAttr(task, "project", project);
        }
        when(execution.getCurrentFlowElement()).thenReturn(task);
        when(execution.getCurrentActivityId()).thenReturn("testActivity");
        when(execution.getVariables()).thenReturn(variables != null ? variables : new HashMap<>());
        return execution;
    }

    @Nested
    @DisplayName("执行指定规则文件")
    class ExecuteRuleFile {

        @Test
        @DisplayName("正常执行规则文件并写回结果")
        void shouldExecuteRuleAndWriteBackResults() throws Exception {
            // Given BPMN 中有 serviceTask，扩展属性 file="rules/rule.xml" project="demo"
            // And KnowledgeService 能加载对应知识包
            // And DelegateExecution 包含流程变量
            KnowledgePackage pkg = mock(KnowledgePackage.class);
            when(knowledgeService.getKnowledge("demo/rules/rule.xml")).thenReturn(pkg);

            KnowledgeSession session = mock(KnowledgeSession.class);
            factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)))
                .thenReturn(session);
            when(session.getParameters()).thenReturn(Map.of("result", "ok"));

            ExecutionResponseImpl response = new ExecutionResponseImpl();
            response.setFiredRules(new ArrayList<>());
            when(session.fireRules()).thenReturn(response);

            Map<String, Object> variables = new HashMap<>();
            DelegateExecution execution = createExecution("rules/rule.xml", "demo", variables);

            // When Flowable 执行到该 serviceTask (delegate.execute(execution))
            delegate.execute(execution);

            // Then 应通过 KnowledgeService 加载知识包
            verify(knowledgeService).getKnowledge("demo/rules/rule.xml");
            // And 创建 KnowledgeSession 并插入流程变量为事实
            factoryMock.verify(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)));
            // And 触发规则执行
            verify(session).fireRules();
            // And 将结果写回流程变量
            verify(execution).setVariables(any(Map.class));
            // And 设置 _firedRules 和 _matchedRules 变量
            verify(execution).setVariable("_firedRules", 0);
            verify(execution).setVariable("_matchedRules", 0);
        }

        @Test
        @DisplayName("规则执行应正确传递参数")
        void shouldPassParametersToSession() throws Exception {
            // Given 知识包中包含参数定义
            KnowledgePackage pkg = mock(KnowledgePackage.class);
            when(knowledgeService.getKnowledge("demo/rules/rule.xml")).thenReturn(pkg);

            KnowledgeSession session = mock(KnowledgeSession.class);
            factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)))
                .thenReturn(session);
            when(session.getParameters()).thenReturn(Map.of("score", "85"));

            ExecutionResponseImpl response = new ExecutionResponseImpl();
            response.setFiredRules(new ArrayList<>());
            when(session.fireRules(any(Map.class))).thenReturn(response);

            Map<String, Object> variables = new HashMap<>();
            variables.put("params", Map.of("input", "test"));
            DelegateExecution execution = createExecution("rules/rule.xml", "demo", variables);

            // When 执行规则时传入参数
            delegate.execute(execution);

            // Then 应将 Map 参数传递给 fireRules
            verify(session).fireRules(any(Map.class));
            // And 规则结果参数应写回流程变量
            verify(execution).setVariables(any(Map.class));
        }
    }

    @Nested
    @DisplayName("规则文件未指定")
    class NoRuleFile {

        @Test
        @DisplayName("file 为空时应跳过执行")
        void shouldSkipWhenFileIsEmpty() throws Exception {
            // Given BPMN 中有 serviceTask，扩展属性 file 为空
            DelegateExecution execution = createExecution("", "demo", new HashMap<>());

            // When Flowable 执行到该 serviceTask
            delegate.execute(execution);

            // Then 应跳过执行（不抛异常）
            verify(knowledgeService, never()).getKnowledge(anyString());
        }

        @Test
        @DisplayName("file 为 null 时应跳过执行")
        void shouldSkipWhenFileIsNull() throws Exception {
            // Given BPMN 中有 serviceTask，扩展属性 file 为 null
            DelegateExecution execution = createExecution(null, "demo", new HashMap<>());

            // When Flowable 执行到该 serviceTask
            delegate.execute(execution);

            // Then 应跳过执行
            verify(knowledgeService, never()).getKnowledge(anyString());
        }
    }

    @Nested
    @DisplayName("知识包加载失败")
    class KnowledgePackageLoadFailure {

        @Test
        @DisplayName("知识包加载失败应抛 RuntimeException")
        void shouldThrowWhenKnowledgePackageNotFound() throws Exception {
            // Given BPMN 中有 serviceTask，扩展属性 file="notexist.xml"
            // And KnowledgeService.getKnowledge 抛出异常
            when(knowledgeService.getKnowledge("notexist.xml")).thenThrow(new RuntimeException("not found"));

            DelegateExecution execution = createExecution("notexist.xml", null, new HashMap<>());

            // When Flowable 执行到该 serviceTask
            // Then 应抛出 RuntimeException，异常消息包含资源 key
            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("notexist.xml");
        }

        @Test
        @DisplayName("知识包为 null 时应跳过并记日志")
        void shouldSkipWhenKnowledgePackageIsNull() throws Exception {
            // Given BPMN 中有 serviceTask，扩展属性 file="valid.xml" project="demo"
            // And KnowledgeService 返回 null
            when(knowledgeService.getKnowledge("demo/valid.xml")).thenReturn(null);

            DelegateExecution execution = createExecution("valid.xml", "demo", new HashMap<>());

            // When Flowable 执行到该 serviceTask
            delegate.execute(execution);

            // Then 应跳过执行（不创建 session）
            factoryMock.verify(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)), never());
        }
    }

    @Nested
    @DisplayName("资源 key 构建")
    class ResourceKeyBuild {

        @Test
        @DisplayName("project 和 file 都有值时应拼接")
        void shouldConcatenateProjectAndFile() throws Exception {
            // Given project="demo" file="rules/rule.xml"
            KnowledgePackage pkg = mock(KnowledgePackage.class);
            when(knowledgeService.getKnowledge("demo/rules/rule.xml")).thenReturn(pkg);

            KnowledgeSession session = mock(KnowledgeSession.class);
            factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)))
                .thenReturn(session);
            ExecutionResponseImpl response = new ExecutionResponseImpl();
            response.setFiredRules(new ArrayList<>());
            when(session.fireRules()).thenReturn(response);

            DelegateExecution execution = createExecution("rules/rule.xml", "demo", new HashMap<>());

            // When 调用 delegate.execute
            delegate.execute(execution);

            // Then 应使用 "demo/rules/rule.xml" 查找知识包
            verify(knowledgeService).getKnowledge("demo/rules/rule.xml");
        }

        @Test
        @DisplayName("project 为空时应通过 buildFromFile 构建知识包")
        void shouldBuildFromFileWhenProjectIsEmpty() throws Exception {
            // Given project 为空 file="rules/rule.xml"
            //     And KnowledgeBuilder 能从文件构建知识包
            KnowledgePackage pkg = mock(KnowledgePackage.class);
            setupKnowledgeBuilder("rules/rule.xml", pkg);

            KnowledgeSession session = mock(KnowledgeSession.class);
            factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)))
                .thenReturn(session);
            ExecutionResponseImpl response = new ExecutionResponseImpl();
            response.setFiredRules(new ArrayList<>());
            when(session.fireRules()).thenReturn(response);

            DelegateExecution execution = createExecution("rules/rule.xml", "", new HashMap<>());

            // When 调用 delegate.execute
            delegate.execute(execution);

            // Then 应通过 buildFromFile 构建知识包（不通过 KnowledgeService）
            verify(knowledgeBuilder).newResourceBase();
            verify(knowledgeService, never()).getKnowledge(anyString());
        }
    }
}
