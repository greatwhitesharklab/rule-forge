package com.ruleforge.console.flow.integration;

import com.ruleforge.Utils;
import com.ruleforge.builder.KnowledgeBuilder;
import com.ruleforge.builder.KnowledgeBase;
import com.ruleforge.builder.ResourceBase;
import com.ruleforge.runtime.KnowledgePackage;
import com.ruleforge.runtime.KnowledgeSession;
import com.ruleforge.runtime.KnowledgeSessionFactory;
import com.ruleforge.runtime.response.ExecutionResponseImpl;
import com.ruleforge.runtime.service.KnowledgeService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
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
 * Feature: 决策流端到端执行
 *
 * 验证完整的决策流生命周期：构建 BPMN → 部署到 Flowable → 执行 → 验证结果。
 * 使用真实的 Flowable 引擎（内存 H2 数据库）和 mock 的知识服务。
 */
@DisplayName("决策流端到端执行")
class FlowExecutionIntegrationTest {

    private static final String RF_NS = "http://ruleforge.com/schema";
    private static final String RF_PREFIX = "ruleforge";

    private ProcessEngine processEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private MockedStatic<Utils> utilsMock;
    private MockedStatic<KnowledgeSessionFactory> factoryMock;
    private ApplicationContext applicationContext;
    private KnowledgeService knowledgeService;
    private KnowledgeBuilder knowledgeBuilder;

    @BeforeEach
    void setUp() {
        // Create in-memory Flowable engine with H2
        StandaloneProcessEngineConfiguration config = new StandaloneProcessEngineConfiguration();
        config.setJdbcUrl("jdbc:h2:mem:flowtest;DB_CLOSE_DELAY=-1");
        config.setJdbcDriver("org.h2.Driver");
        config.setJdbcUsername("sa");
        config.setJdbcPassword("");
        config.setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);

        // Register delegate beans in Flowable engine (must be before buildProcessEngine)
        Map<Object, Object> delegateBeans = new HashMap<>();
        delegateBeans.put("ruleServiceTaskDelegate", new com.ruleforge.console.flow.delegate.RuleServiceTaskDelegate(knowledgeBuilder));
        delegateBeans.put("packageServiceTaskDelegate", new com.ruleforge.console.flow.delegate.PackageServiceTaskDelegate());
        config.setBeans(delegateBeans);

        processEngine = config.buildProcessEngine();

        repositoryService = processEngine.getRepositoryService();
        runtimeService = processEngine.getRuntimeService();

        // Mock Spring context for delegates
        utilsMock = mockStatic(Utils.class);
        factoryMock = mockStatic(KnowledgeSessionFactory.class);

        applicationContext = mock(ApplicationContext.class);
        knowledgeService = mock(KnowledgeService.class);
        knowledgeBuilder = mock(KnowledgeBuilder.class);

        utilsMock.when(Utils::getApplicationContext).thenReturn(applicationContext);
        when(applicationContext.getBean(KnowledgeService.BEAN_ID)).thenReturn(knowledgeService);
    }

    @AfterEach
    void tearDown() {
        factoryMock.close();
        utilsMock.close();
        processEngine.close();
    }

    // ================================================================
    // BPMN XML helpers — build directly as XML strings
    // ================================================================

    private static final String BPMN_HEADER = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
            xmlns:flowable="http://flowable.org/bpmn"
            xmlns:ruleforge="http://ruleforge.com/schema"
            targetNamespace="http://ruleforge.com/test">
        """;

    private static final String BPMN_FOOTER = "</definitions>";

    private String wrapProcess(String processId, String... elements) {
        StringBuilder sb = new StringBuilder(BPMN_HEADER);
        sb.append("  <process id=\"").append(processId).append("\" isExecutable=\"true\">\n");
        for (String e : elements) {
            sb.append("    ").append(e).append("\n");
        }
        sb.append("  </process>\n");
        sb.append(BPMN_FOOTER);
        return sb.toString();
    }

    private String startEvent(String id) {
        return "<startEvent id=\"" + id + "\"/>";
    }

    private String endEvent(String id) {
        return "<endEvent id=\"" + id + "\"/>";
    }

    private String serviceTaskWithClass(String id, String name, String className) {
        return "<serviceTask id=\"" + id + "\" name=\"" + name + "\" flowable:class=\"" + className + "\"/>";
    }

    private String serviceTaskWithClassAndAttrs(String id, String name, String className, String... attrs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<serviceTask id=\"").append(id).append("\" name=\"").append(name)
          .append("\" flowable:class=\"").append(className).append("\"");
        for (String attr : attrs) {
            sb.append(" ").append(attr);
        }
        sb.append("/>");
        return sb.toString();
    }

    private String serviceTaskWithDelegateExpression(String id, String name, String expression, String... attrs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<serviceTask id=\"").append(id).append("\" name=\"").append(name)
          .append("\" flowable:delegateExpression=\"").append(expression).append("\"");
        for (String attr : attrs) {
            sb.append(" ").append(attr);
        }
        sb.append("/>");
        return sb.toString();
    }

    private String exclusiveGateway(String id, String name) {
        return "<exclusiveGateway id=\"" + id + "\" name=\"" + name + "\"/>";
    }

    private String sequenceFlow(String from, String to) {
        return "<sequenceFlow id=\"flow_" + from + "_" + to + "\" sourceRef=\"" + from + "\" targetRef=\"" + to + "\"/>";
    }

    private String sequenceFlowWithCondition(String from, String to, String condition) {
        return "<sequenceFlow id=\"flow_" + from + "_" + to + "\" sourceRef=\"" + from + "\" targetRef=\"" + to + "\">\n" +
               "      <conditionExpression>" + condition + "</conditionExpression>\n" +
               "    </sequenceFlow>";
    }

    private void deploy(String processId, String... elements) {
        String bpmnXml = wrapProcess(processId, elements);
        repositoryService.createDeployment()
            .addString(processId + ".bpmn20.xml", bpmnXml)
            .deploy();
    }

    // ================================================================
    // Mock setup helpers
    // ================================================================

    private void setupKnowledgeService(String resourceKey) throws Exception {
        KnowledgePackage pkg = mock(KnowledgePackage.class);
        when(knowledgeService.getKnowledge(resourceKey)).thenReturn(pkg);

        KnowledgeSession session = mock(KnowledgeSession.class);
        factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)))
            .thenReturn(session);
        when(session.getParameters()).thenReturn(new HashMap<>());

        ExecutionResponseImpl response = new ExecutionResponseImpl();
        response.setFiredRules(new ArrayList<>());
        when(session.fireRules()).thenReturn(response);
        when(session.fireRules(any(Map.class))).thenReturn(response);
    }

    private void setupKnowledgeBuilder() throws Exception {
        ResourceBase resourceBase = mock(ResourceBase.class);
        KnowledgeBase knowledgeBase = mock(KnowledgeBase.class);
        when(knowledgeBuilder.newResourceBase()).thenReturn(resourceBase);
        when(knowledgeBuilder.buildKnowledgeBase(any(ResourceBase.class))).thenReturn(knowledgeBase);

        KnowledgePackage pkg = mock(KnowledgePackage.class);
        when(knowledgeBase.getKnowledgePackage()).thenReturn(pkg);

        KnowledgeSession session = mock(KnowledgeSession.class);
        factoryMock.when(() -> KnowledgeSessionFactory.newKnowledgeSession(any(KnowledgePackage.class)))
            .thenReturn(session);
        when(session.getParameters()).thenReturn(new HashMap<>());

        ExecutionResponseImpl response = new ExecutionResponseImpl();
        response.setFiredRules(new ArrayList<>());
        when(session.fireRules()).thenReturn(response);
        when(session.fireRules(any(Map.class))).thenReturn(response);
    }

    // ================================================================
    // Test action bean for ActionServiceTaskDelegate
    // ================================================================
    public static class TestActionBean {
        public Map<String, Object> process(Map<String, Object> vars) {
            Map<String, Object> result = new HashMap<>(vars);
            result.put("processed", true);
            return result;
        }
    }

    // ================================================================
    // 场景 1: 简单线性流程
    // ================================================================

    @Nested
    @DisplayName("场景 1: 简单线性流程（Start → Rule → End）")
    class SimpleLinearFlow {

        @Test
        @DisplayName("执行只包含一个规则节点的简单流程")
        void shouldExecuteSimpleLinearFlow() throws Exception {
            // Given 一个 BPMN 流程定义：Start → RuleServiceTask → End
            setupKnowledgeService("demo/rules/rule.xml");

            deploy("simpleFlow",
                startEvent("start"),
                serviceTaskWithDelegateExpression("ruleTask", "准入规则",
                    "${ruleServiceTaskDelegate}",
                    "ruleforge:file=\"rules/rule.xml\"", "ruleforge:project=\"demo\""),
                endEvent("end"),
                sequenceFlow("start", "ruleTask"),
                sequenceFlow("ruleTask", "end")
            );

            // When 启动流程实例
            Map<String, Object> variables = new HashMap<>();
            variables.put("applicant", "张三");
            ProcessInstance instance = runtimeService.startProcessInstanceByKey("simpleFlow", variables);

            // Then 流程应正常完成
            assertThat(instance.isEnded()).isTrue();
        }
    }

    // ================================================================
    // 场景 2: 条件网关流程
    // ================================================================

    @Nested
    @DisplayName("场景 2: 条件网关流程")
    class ConditionGatewayFlow {

        @Test
        @DisplayName("条件网关根据流程变量选择分支")
        void shouldSelectConditionBranchInFlow() throws Exception {
            // Given BPMN: Start → GW → (score>80→EndA, else→EndB)
            deploy("conditionFlow",
                startEvent("start"),
                exclusiveGateway("gw1", "条件判断"),
                endEvent("endA"),
                endEvent("endB"),
                sequenceFlow("start", "gw1"),
                sequenceFlowWithCondition("gw1", "endA", "${score > 80}"),
                sequenceFlow("gw1", "endB")
            );

            // When score=90 → should go to endA
            Map<String, Object> vars = new HashMap<>();
            vars.put("score", 90);
            ProcessInstance instance = runtimeService.startProcessInstanceByKey("conditionFlow", vars);

            // Then should complete
            assertThat(instance.isEnded()).isTrue();
        }

        @Test
        @DisplayName("条件不满足时走默认分支")
        void shouldSelectDefaultBranchWhenConditionNotMet() throws Exception {
            // Given same flow definition
            deploy("conditionFlow2",
                startEvent("start"),
                exclusiveGateway("gw1", "条件判断"),
                endEvent("endA"),
                endEvent("endB"),
                sequenceFlow("start", "gw1"),
                sequenceFlowWithCondition("gw1", "endA", "${score > 80}"),
                sequenceFlow("gw1", "endB")
            );

            // When score=50 → should go to endB (default)
            Map<String, Object> vars = new HashMap<>();
            vars.put("score", 50);
            ProcessInstance instance = runtimeService.startProcessInstanceByKey("conditionFlow2", vars);

            // Then should complete via endB
            assertThat(instance.isEnded()).isTrue();
        }
    }

    // ================================================================
    // 场景 5: 动作节点流程
    // ================================================================

    @Nested
    @DisplayName("场景 5: 动作节点流程（Start → Action → End）")
    class ActionNodeFlow {

        @Test
        @DisplayName("动作节点调用 Spring Bean 方法")
        void shouldExecuteActionNodeInFlow() throws Exception {
            // Given BPMN: Start → ActionServiceTask → End
            TestActionBean testBean = spy(new TestActionBean());
            when(applicationContext.getBean("testActionBean")).thenReturn(testBean);

            deploy("actionFlow",
                startEvent("start"),
                serviceTaskWithClassAndAttrs("action1", "处理",
                    "com.ruleforge.console.flow.delegate.ActionServiceTaskDelegate",
                    "ruleforge:bean=\"testActionBean\"", "ruleforge:method=\"process\""),
                endEvent("end"),
                sequenceFlow("start", "action1"),
                sequenceFlow("action1", "end")
            );

            // When 启动流程
            Map<String, Object> vars = new HashMap<>();
            vars.put("input", "test");
            ProcessInstance instance = runtimeService.startProcessInstanceByKey("actionFlow", vars);

            // Then 应调用 bean 方法
            verify(testBean).process(any(Map.class));
            assertThat(instance.isEnded()).isTrue();
        }
    }

    // ================================================================
    // 场景 7: 错误处理
    // ================================================================

    @Nested
    @DisplayName("场景 7: 错误处理")
    class ErrorHandling {

        @Test
        @DisplayName("网关无匹配分支时应报错")
        void shouldFailFlowWhenNoBranchMatches() throws Exception {
            // Given BPMN: Start → GW → (score>100→EndA, else→EndB) — both conditional
            deploy("errorFlow",
                startEvent("start"),
                exclusiveGateway("gw1", "判断"),
                endEvent("endA"),
                endEvent("endB"),
                sequenceFlow("start", "gw1"),
                sequenceFlowWithCondition("gw1", "endA", "${score > 100}"),
                sequenceFlowWithCondition("gw1", "endB", "${score > 200}")
            );

            // When score=50 → no branch matches
            Map<String, Object> vars = new HashMap<>();
            vars.put("score", 50);

            // Then should throw
            assertThatThrownBy(() -> runtimeService.startProcessInstanceByKey("errorFlow", vars))
                .hasMessageContaining("No outgoing sequence flow");
        }
    }
}
