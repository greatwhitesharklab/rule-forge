package com.ruleforge.console.flow.delegate;

import com.ruleforge.Utils;
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
 * Feature: 知识包节点执行
 *
 * PackageServiceTaskDelegate 负责执行 BPMN 中 taskType="package" 的 serviceTask 节点。
 * 该节点通过 packageId 和 project 属性加载预编译的知识包并执行规则。
 */
@DisplayName("PackageServiceTaskDelegate - 知识包节点执行")
class PackageServiceTaskDelegateTest {

    private static final String RF_NS = "http://ruleforge.com/schema";

    private PackageServiceTaskDelegate delegate;
    private MockedStatic<Utils> utilsMock;
    private MockedStatic<KnowledgeSessionFactory> factoryMock;
    private ApplicationContext applicationContext;
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        delegate = new PackageServiceTaskDelegate();
        utilsMock = mockStatic(Utils.class);
        factoryMock = mockStatic(KnowledgeSessionFactory.class);

        applicationContext = mock(ApplicationContext.class);
        knowledgeService = mock(KnowledgeService.class);
        utilsMock.when(Utils::getApplicationContext).thenReturn(applicationContext);
        when(applicationContext.getBean(KnowledgeService.BEAN_ID)).thenReturn(knowledgeService);
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

    private DelegateExecution createExecution(String packageId, String project, Map<String, Object> variables) {
        DelegateExecution execution = mock(DelegateExecution.class);
        ServiceTask task = new ServiceTask();
        if (packageId != null) {
            addExtensionAttr(task, "packageId", packageId);
        }
        if (project != null) {
            addExtensionAttr(task, "project", project);
        }
        when(execution.getCurrentFlowElement()).thenReturn(task);
        when(execution.getCurrentActivityId()).thenReturn("pkgTask_1");
        when(execution.getVariables()).thenReturn(variables != null ? variables : new HashMap<>());
        return execution;
    }

    private void setupSuccessfulExecution(String resourceKey) throws Exception {
        KnowledgePackage pkg = mock(KnowledgePackage.class);
        when(knowledgeService.getKnowledge(resourceKey)).thenReturn(pkg);

        KnowledgeSession session = mock(KnowledgeSession.class);
        factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)))
            .thenReturn(session);
        when(session.getParameters()).thenReturn(new HashMap<>());

        ExecutionResponseImpl response = new ExecutionResponseImpl();
        response.setFiredRules(new ArrayList<>());
        when(session.fireRules()).thenReturn(response);
    }

    @Nested
    @DisplayName("正常知识包执行")
    class NormalPackageExecution {

        @Test
        @DisplayName("加载指定项目的知识包并执行规则")
        void shouldLoadAndExecuteKnowledgePackage() throws Exception {
            // Given BPMN 中有 serviceTask，扩展属性 packageId="risk-rules" project="loan"
            //     And KnowledgeService 能加载知识包 "loan/risk-rules"
            setupSuccessfulExecution("loan/risk-rules");
            DelegateExecution execution = createExecution("risk-rules", "loan", new HashMap<>());

            // When Flowable 执行到该 serviceTask
            delegate.execute(execution);

            // Then 应通过 KnowledgeService 加载知识包 "loan/risk-rules"
            verify(knowledgeService).getKnowledge("loan/risk-rules");
            // And 创建 KnowledgeSession 并执行规则
            factoryMock.verify(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)));
            // And 规则结果写回流程变量
            verify(execution).setVariables(any(Map.class));
        }

        @Test
        @DisplayName("执行知识包后应设置 _firedRules 和 _matchedRules")
        void shouldSetFiredAndMatchedRuleCounts() throws Exception {
            // Given 知识包执行后返回 firedRules
            setupSuccessfulExecution("demo/rules");
            DelegateExecution execution = createExecution("rules", "demo", new HashMap<>());

            // When 执行知识包
            delegate.execute(execution);

            // Then 流程变量 _firedRules 应设置
            verify(execution).setVariable(eq("_firedRules"), anyInt());
            verify(execution).setVariable(eq("_matchedRules"), anyInt());
        }

        @Test
        @DisplayName("知识包执行应正确传递流程变量作为事实")
        void shouldPassProcessVariablesAsFacts() throws Exception {
            // Given DelegateExecution 包含变量
            setupSuccessfulExecution("demo/pkg");
            Map<String, Object> variables = new HashMap<>();
            variables.put("score", 85);
            DelegateExecution execution = createExecution("pkg", "demo", variables);

            // When 执行知识包
            delegate.execute(execution);

            // Then 应创建 session 并写回结果
            verify(execution).setVariables(any(Map.class));
        }
    }

    @Nested
    @DisplayName("知识包标识缺失")
    class PackageIdMissing {

        @Test
        @DisplayName("packageId 为空时应跳过执行")
        void shouldSkipWhenPackageIdIsEmpty() throws Exception {
            // Given BPMN 中有 serviceTask，扩展属性 packageId 为空
            DelegateExecution execution = createExecution("", "demo", new HashMap<>());

            // When Flowable 执行到该 serviceTask
            delegate.execute(execution);

            // Then 应跳过执行
            verify(knowledgeService, never()).getKnowledge(anyString());
        }

        @Test
        @DisplayName("packageId 为 null 时应跳过执行")
        void shouldSkipWhenPackageIdIsNull() throws Exception {
            // Given BPMN 中有 serviceTask，扩展属性 packageId 为 null
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
        @DisplayName("知识包不存在时应抛出 RuntimeException")
        void shouldThrowWhenKnowledgePackageNotFound() throws Exception {
            // Given BPMN 中有 serviceTask，扩展属性 packageId="not-exist" project="demo"
            //     And KnowledgeService.getKnowledge("demo/not-exist") 返回 null
            when(knowledgeService.getKnowledge("demo/not-exist")).thenReturn(null);
            DelegateExecution execution = createExecution("not-exist", "demo", new HashMap<>());

            // When Flowable 执行到该 serviceTask
            delegate.execute(execution);

            // Then 应不创建 session（跳过）
            factoryMock.verify(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)), never());
        }

        @Test
        @DisplayName("KnowledgeService 抛出异常时应向上传播")
        void shouldPropagateKnowledgeServiceException() throws Exception {
            // Given KnowledgeService.getKnowledge 抛出 RuntimeException("load failed")
            when(knowledgeService.getKnowledge("demo/pkg"))
                .thenThrow(new RuntimeException("load failed"));
            DelegateExecution execution = createExecution("pkg", "demo", new HashMap<>());

            // When 执行 delegate
            // Then 应抛出 RuntimeException 包装原始异常
            assertThatThrownBy(() -> delegate.execute(execution))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("pkg");
        }
    }

    @Nested
    @DisplayName("资源 key 构建")
    class ResourceKeyBuild {

        @Test
        @DisplayName("project 和 packageId 都有值时应拼接为 project/packageId")
        void shouldBuildResourceKeyFromProjectAndPackageId() throws Exception {
            // Given project="loan" packageId="credit-score"
            setupSuccessfulExecution("loan/credit-score");
            DelegateExecution execution = createExecution("credit-score", "loan", new HashMap<>());

            // When 执行 delegate
            delegate.execute(execution);

            // Then 应使用 "loan/credit-score" 查找知识包
            verify(knowledgeService).getKnowledge("loan/credit-score");
        }

        @Test
        @DisplayName("project 为空时应只使用 packageId")
        void shouldUsePackageIdOnlyWhenProjectIsEmpty() throws Exception {
            // Given project 为空 packageId="credit-score"
            setupSuccessfulExecution("credit-score");
            DelegateExecution execution = createExecution("credit-score", "", new HashMap<>());

            // When 执行 delegate
            delegate.execute(execution);

            // Then 应使用 "credit-score" 查找知识包
            verify(knowledgeService).getKnowledge("credit-score");
        }
    }
}
